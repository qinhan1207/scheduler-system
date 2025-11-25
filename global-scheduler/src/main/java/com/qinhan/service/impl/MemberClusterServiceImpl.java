package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.AnomalyDetectionService;
import com.qinhan.service.MemberClusterService;
import com.qinhan.service.ResourceSimulatorService;
import com.qinhan.util.HealthEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberClusterServiceImpl implements MemberClusterService {

    private final ConcurrentHashMap<String, ClusterStatus> clusterMap = new ConcurrentHashMap<>();

    // 注入异常检测服务 (包含 EWMA 预测)
    private final AnomalyDetectionService anomalyDetectionService;

    // 注入资源模拟器 (如果还需要补全 CPU/Mem 的话，如果 LSA 已经采集了真实值，这个可以考虑移除了)
    private final ResourceSimulatorService resourceSimulatorService;

    @Override
    public void updateClusterStatus(ClusterStatus status) {
        // 1. 数据补全 (如果 LSA 发来的数据某些字段为空，这里做最后兜底)
        // 如果 LSA 现在已经能发真实的 CPU/Mem，这行可以注释掉，或者保留作为防卫性编程
        status = resourceSimulatorService.enrichDynamicMetrics(status);

        // 2. 🔥 核心优化：实时触发预测与异常检测
        // 收到数据立刻算，不要等定时任务
        anomalyDetectionService.detectClusterAnomaly(status);

        // 3. 计算常规健康分 (Health Score - 用于展示)
        // 注意：StabilityScore 是用于调度的，HealthScore 是用于大屏展示的
        double healthScore = HealthEvaluator.calculateScore(status);
        String healthStatus = HealthEvaluator.evaluate(status);

        status.setHealthScore(healthScore);
        status.setHealthStatus(healthStatus);

        // 4. 更新内存缓存
        clusterMap.put(status.getClusterName(), status);

        log.debug("✅ 集群 [{}] 更新完毕: Health={}, Stability={}",
                status.getClusterName(),
                String.format("%.0f", healthScore),
                String.format("%.0f", status.getStabilityScore()));
    }

    @Override
    public List<ClusterStatus> getAllClusterStatus() {
        return new ArrayList<>(clusterMap.values());
    }


}