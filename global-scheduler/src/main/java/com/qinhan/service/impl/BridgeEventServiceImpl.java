package com.qinhan.service.impl;

import com.qinhan.decision.GlobalDecisionEngine;
import com.qinhan.model.ScheduleDecision;
import com.qinhan.model.SchedulingEvent;
import com.qinhan.model.TentativeRecord;
import com.qinhan.service.BindingUpdaterService;
import com.qinhan.service.BridgeEventService;
import com.qinhan.service.TentativeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BridgeEventServiceImpl implements BridgeEventService {

    @Autowired
    private GlobalDecisionEngine globalDecisionEngine;

    @Autowired
    private BindingUpdaterService bindingUpdaterService;

    private final Map<String, SchedulingEvent> eventStore = new ConcurrentHashMap<>();
    private final TentativeService tentativeService;

    public BridgeEventServiceImpl(TentativeService tentativeService) {
        this.tentativeService = tentativeService;
    }

    /**
     * 处理Bridge上报的ResourceBinding事件
     */
    @Override
    public void processBridgeEvent(SchedulingEvent event) {
        try {
            // 存储事件
            String eventKey = event.getNamespace() + "/" + event.getName();
            eventStore.put(eventKey, event);

            log.info("📥 存储Bridge事件: {} - {}/{} - 集群: {}",
                    event.getEventType(), event.getNamespace(), event.getName(),
                    event.getTargetClusters());

            // 如果是ADDED事件，创建tentative记录供GS评估
            if ("ADDED".equals(event.getEventType()) && event.isScheduled()) {
                createTentativeRecordFromEvent(event);
            }

            // 可以在这里添加更多业务逻辑，比如：
            // - 分析调度决策是否合理
            // - 记录调度历史用于优化
            // - 触发重新调度评估
            ScheduleDecision decision = globalDecisionEngine.evaluateSchedulingEvent(event);
            if (decision.isNeedReschedule()) {
                log.info("🚀 触发重新调度: workload={} -> 新集群={}",
                        decision.getWorkloadName(), decision.getRecommendedCluster());
                // TODO: 调用绑定更新逻辑
                bindingUpdaterService.updateBinding(decision);
            } else {
                log.info("✅ 不需要重新调度: {}", decision.getReason());
            }

        } catch (Exception e) {
            log.error("❌ 处理Bridge事件失败: {}/{}", event.getNamespace(), event.getName(), e);
        }
    }

    /**
     * 获取所有Bridge事件记录
     */
    @Override
    public List<SchedulingEvent> getAllBridgeEvents() {
        return new ArrayList<>(eventStore.values());
    }

    /**
     * 根据工作负载名称查找事件
     */
    @Override
    public List<SchedulingEvent> getEventsByWorkload(String workloadName) {
        return eventStore.values().stream()
                .filter(event -> workloadName.equals(event.getWorkloadName()))
                .collect(Collectors.toList());
    }

    /**
     * 从Bridge事件创建Tentative记录
     */
    private void createTentativeRecordFromEvent(SchedulingEvent event) {
        if (event.getTargetClusters() == null || event.getTargetClusters().isEmpty()) {
            return;
        }

        // 为每个目标集群创建tentative记录
        for (String cluster : event.getTargetClusters()) {
            TentativeRecord record = new TentativeRecord();
            record.setPodName(event.getWorkloadName() + "-" + cluster); // 唯一标识
            record.setNamespace(event.getNamespace());
            record.setCluster(cluster);
            record.setScore(calculateInitialScore(event, cluster));
            record.setReason("From Bridge monitoring - " + event.getEventType());
            record.setState("observed"); // 新状态，表示这是观察到的调度决策

            tentativeService.addTentative(record);
        }

        log.info("🔄 为事件 {}/{} 创建了 {} 个tentative记录",
                event.getNamespace(), event.getName(), event.getTargetClusters().size());
    }

    /**
     * 计算初始评分（可以根据具体策略调整）
     */
    private double calculateInitialScore(SchedulingEvent event, String cluster) {
        // 基础评分逻辑，可以根据实际需求调整
        double baseScore = 50.0;

        // ADDED事件可能有更高权重
        if ("ADDED".equals(event.getEventType())) {
            baseScore += 20.0;
        }

        // 调度成功的加分
        if (event.isScheduled()) {
            baseScore += 10.0;
        }

        // 可以根据集群名称等因素调整
        if (cluster.contains("cluster01")) {
            baseScore += 5.0; // 给cluster01稍微高一点的初始分
        }

        return baseScore;
    }
}