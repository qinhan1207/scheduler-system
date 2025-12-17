package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.RawNetworkStats;
import com.qinhan.service.AnomalyDetectionService;
import com.qinhan.service.MemberClusterService;
import com.qinhan.util.HealthEvaluator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberClusterServiceImpl implements MemberClusterService {

    private final ConcurrentHashMap<String, ClusterStatus> clusterMap = new ConcurrentHashMap<>();

    private static final double LATENCY_SLA_THRESHOLD = 500.0;
    private static final double MAX_PENALTY_LATENCY = 2000.0;
    private static final double LOSS_RATE_PENALTY_WEIGHT = 20.0;

    // 注入异常检测服务 (包含 EWMA 预测)
    private final AnomalyDetectionService anomalyDetectionService;

    @Override
    public void updateClusterStatus(ClusterStatus status) {
        // 1. 聚合网络探测结果：LSA 只上报原始探测值，这里做延迟/丢包的统一口径计算
        status = aggregateNetworkMetrics(status);

        // 2. 🔥 核心优化：实时触发预测与异常检测
        // 收到数据立刻算，不要等定时任务
        anomalyDetectionService.detectClusterAnomaly(status);

        // 3. 计算常规健康分 (Health Score - 用于展示)
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


    private ClusterStatus aggregateNetworkMetrics(ClusterStatus status) {
        Map<String, RawNetworkStats> rawMap = status.getPeerRawStats();

        if (rawMap == null || rawMap.isEmpty()) {
            status.setNetworkLatency(MAX_PENALTY_LATENCY);
            status.setPacketLossRate(100.0);
            status.setPeerLatencyMap(new HashMap<>());
            return status;
        }

        Map<String, Double> peerLatencyMap = new HashMap<>();
        double validLatencySum = 0.0;
        int validLinkCount = 0;
        double totalRawLossRate = 0.0;
        int countedLinks = 0;

        for (Map.Entry<String, RawNetworkStats> entry : rawMap.entrySet()) {
            RawNetworkStats stats = entry.getValue();
            if (stats == null) {
                continue;
            }

            double avgLatency = stats.getAvgLatency();
            double lossRate = stats.getLossRate();
            boolean isLinkHealthy = (lossRate < 0.1) && (avgLatency < LATENCY_SLA_THRESHOLD);

            double displayLatency = avgLatency;
            if (lossRate > 0) {
                displayLatency += lossRate * LOSS_RATE_PENALTY_WEIGHT;
            }
            peerLatencyMap.put(entry.getKey(), displayLatency);

            if (isLinkHealthy) {
                validLatencySum += avgLatency;
                validLinkCount++;
            }

            totalRawLossRate += lossRate;
            countedLinks++;
        }

        double networkLatency = validLinkCount > 0
                ? validLatencySum / validLinkCount
                : MAX_PENALTY_LATENCY;

        double packetLossRate = validLinkCount > 0
                ? 0.0
                : (countedLinks > 0 ? totalRawLossRate / countedLinks : 100.0);

        status.setNetworkLatency(networkLatency);
        status.setPacketLossRate(packetLossRate);
        status.setPeerLatencyMap(peerLatencyMap);

        return status;
    }


}