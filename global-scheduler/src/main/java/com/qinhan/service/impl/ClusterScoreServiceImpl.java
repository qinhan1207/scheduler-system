package com.qinhan.service.impl;

import com.qinhan.model.ClusterScore;
import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ClusterScoreService;
import com.qinhan.service.MemberClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ClusterScoreServiceImpl
 * 简单实现：基于 ClusterStatus 中的 CPU、内存使用率计算健康分。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterScoreServiceImpl implements ClusterScoreService {

    private final MemberClusterService memberClusterService;

    @Override
    public ClusterScore calculateScore(String clusterName) {

        List<ClusterStatus> allStatus = memberClusterService.getAllClusterStatus();

        ClusterStatus status = allStatus.stream()
                .filter(s -> clusterName.equals(s.getClusterName()))
                .findFirst()
                .orElse(null);

        if (status == null) {
            log.warn("⚠️ 未找到集群 [{}] 的状态记录，无法计算评分。", clusterName);
            return ClusterScore.builder()
                    .clusterName(clusterName)
                    .healthScore(0)
                    .reason("未找到集群状态数据")
                    .build();
        }


        double cpu = status.getCpuUsage();
        double mem = status.getMemoryUsage();

        // 简单的健康评分算法（后续可以接 ML 模型）
        double score = status.getHealthScore();

        String reason;
        if ("Critical".equalsIgnoreCase(status.getHealthStatus())) {
            reason = "集群处于危险状态，负载过高";
        } else if ("Warning".equalsIgnoreCase(status.getHealthStatus())) {
            reason = "集群负载较高，建议谨慎调度";
        } else if ("Healthy".equalsIgnoreCase(status.getHealthStatus())) {
            reason = "集群运行稳定，资源充足";
        } else {
            reason = "未知状态";
        }

        ClusterScore result = ClusterScore.builder()
                .clusterName(clusterName)
                .healthScore(score)
                .reason(reason)
                .build();

        log.info("📊 从 MemberCluster 读取评分结果: {} => 分数={} 状态={}",
                clusterName, String.format("%.2f", score), status.getHealthStatus());

        return result;
    }
}
