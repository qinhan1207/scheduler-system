package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.ScheduleDecision;
import com.qinhan.model.TentativeRecord;
import com.qinhan.service.BindingUpdaterService;
import com.qinhan.service.MemberClusterService;
import com.qinhan.service.TentativeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Tentative 调度协调服务实现
 */
@Slf4j
@Service
public class TentativeServiceImpl implements TentativeService {
    
    private final Map<String, TentativeRecord> recordStore = new ConcurrentHashMap<>();
    private final MemberClusterService memberClusterService;
    private final BindingUpdaterService bindingUpdaterService;
    
    public TentativeServiceImpl(MemberClusterService memberClusterService,
                               BindingUpdaterService bindingUpdaterService) {
        this.memberClusterService = memberClusterService;
        this.bindingUpdaterService = bindingUpdaterService;
    }


    /**
     * 创建新的调度建议
     */
    @Override
    public String createSchedulingProposal(TentativeRecord record) {
        String recordId = UUID.randomUUID().toString();
        record.setRecordId(recordId);
        record.setState("PENDING");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        
        recordStore.put(recordId, record);
        
        log.info("📝 创建调度建议: ID={}, {}/{} -> {} (分数: {})",
                recordId, record.getNamespace(), record.getWorkloadName(),
                record.getRecommendedCluster(), record.getScore());
                
        return recordId;
    }


    /**
     * 处理待定的调度建议（确认/拒绝）
     */
    @Override
    @Scheduled(fixedDelay = 5000) // 每5秒处理一次
    public void processPendingProposals() {
        log.info("🔄 开始处理待定调度建议...");
        
        List<TentativeRecord> pendingRecords = getRecordsByState("PENDING");
        if (pendingRecords.isEmpty()) {
            return;
        }
        
        for (TentativeRecord record : pendingRecords) {
            try {
                processSingleProposal(record);
            } catch (Exception e) {
                log.error("❌ 处理调度建议失败: {}", record.getRecordId(), e);
                record.setState("ERROR");
                record.setDecisionReason("处理异常: " + e.getMessage());
                record.setUpdatedAt(LocalDateTime.now());
            }
        }
        
        log.info("✅ 待定调度建议处理完成，共处理 {} 条", pendingRecords.size());
    }
    
    /**
     * 处理单个调度建议
     */
    private void processSingleProposal(TentativeRecord record) {
        // 🎯 决策逻辑：二次验证集群状态
        boolean shouldConfirm = evaluateProposal(record);
        
        if (shouldConfirm) {
            confirmProposal(record.getRecordId(), "自动确认: 集群状态良好");
        } else {
            rejectProposal(record.getRecordId(), "自动拒绝: 集群状态不满足要求");
        }
    }
    
    /**
     * 评估调度建议是否应该确认
     */
    private boolean evaluateProposal(TentativeRecord record) {
        try {
            // 1. 检查推荐集群是否存在且健康
            List<ClusterStatus> allClusters = memberClusterService.getAllClusterStatus();
            Optional<ClusterStatus> targetCluster = allClusters.stream()
                    .filter(c -> c.getClusterName().equals(record.getRecommendedCluster()))
                    .findFirst();
                    
            if (targetCluster.isEmpty()) {
                log.warn("⚠️ 推荐集群不存在: {}", record.getRecommendedCluster());
                return false;
            }
            
            ClusterStatus cluster = targetCluster.get();
            
            // 2. 检查健康分是否仍然良好
            if (cluster.getHealthScore() < 60.0) {
                log.warn("⚠️ 集群健康分过低: {} (当前: {})", 
                        cluster.getClusterName(), cluster.getHealthScore());
                return false;
            }
            
            // 3. 检查集群状态
            if ("Critical".equals(cluster.getHealthStatus())) {
                log.warn("⚠️ 集群状态严重: {}", cluster.getClusterName());
                return false;
            }
            
            log.info("✅ 调度建议评估通过: {} -> {} (健康分: {})",
                    record.getWorkloadName(), record.getRecommendedCluster(), 
                    cluster.getHealthScore());
            return true;
            
        } catch (Exception e) {
            log.error("❌ 评估调度建议失败: {}", record.getRecordId(), e);
            return false;
        }
    }


    /**
     * 手动确认调度建议
     */
    @Override
    public boolean confirmProposal(String recordId, String reason) {
        TentativeRecord record = recordStore.get(recordId);
        if (record == null || !"PENDING".equals(record.getState())) {
            log.warn("⚠️ 无法确认不存在的或非PENDING状态的记录: {}", recordId);
            return false;
        }
        
        try {
            // 创建调度决策并执行
            ScheduleDecision decision = ScheduleDecision.builder()
                    .workloadName(record.getWorkloadName())
                    .namespace(record.getNamespace())
                    .recommendedCluster(record.getRecommendedCluster())
                    .recommendedScore(record.getScore())
                    .needReschedule(true)
                    .reason("Tentative确认 - " + reason)
                    .build();
            
            // 执行调度
            bindingUpdaterService.updateBinding(decision);
            
            // 更新记录状态
            record.setState("CONFIRMED");
            record.setDecisionReason(reason);
            record.setUpdatedAt(LocalDateTime.now());
            
            log.info("✅ 确认调度建议: {} -> {}", 
                    record.getWorkloadName(), record.getRecommendedCluster());
            return true;
            
        } catch (Exception e) {
            log.error("❌ 确认调度建议失败: {}", recordId, e);
            record.setState("ERROR");
            record.setDecisionReason("确认失败: " + e.getMessage());
            record.setUpdatedAt(LocalDateTime.now());
            return false;
        }
    }

    /**
     * 手动拒绝调度建议
     */
    @Override
    public boolean rejectProposal(String recordId, String reason) {
        TentativeRecord record = recordStore.get(recordId);
        if (record == null || !"PENDING".equals(record.getState())) {
            log.warn("⚠️ 无法拒绝不存在的或非PENDING状态的记录: {}", recordId);
            return false;
        }
        
        record.setState("REJECTED");
        record.setDecisionReason(reason);
        record.setUpdatedAt(LocalDateTime.now());
        
        log.info("❌ 拒绝调度建议: {} -> {} (原因: {})",
                record.getWorkloadName(), record.getRecommendedCluster(), reason);
        return true;
    }


    /**
     * 获取所有调度建议记录
     */
    @Override
    public List<TentativeRecord> getAllRecords() {
        return new ArrayList<>(recordStore.values());
    }

    /**
     * 根据状态获取记录
     */
    @Override
    public List<TentativeRecord> getRecordsByState(String state) {
        return recordStore.values().stream()
                .filter(record -> state.equals(record.getState()))
                .collect(Collectors.toList());
    }


    /**
     * 获取特定工作负载的记录
     */
    @Override
    public List<TentativeRecord> getRecordsByWorkload(String workloadName) {
        return recordStore.values().stream()
                .filter(record -> workloadName.equals(record.getWorkloadName()))
                .collect(Collectors.toList());
    }
}