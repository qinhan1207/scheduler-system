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



        // 简单的健康评分算法（后续可以接 ML 模型）
        double score = status.getHealthScore();

        String reason;
        switch (status.getHealthStatus().toLowerCase()) {
            case "critical" ->
                    reason = "集群网络或存储状态异常，延迟或丢包率过高";
            case "warning" ->
                    reason = "网络波动或存储占用较高，建议降低调度压力";
            case "healthy" ->
                    reason = "网络畅通，带宽充足，集群运行稳定";
            default ->
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
