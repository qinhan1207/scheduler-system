package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.RawNetworkStats;
import com.qinhan.service.NetworkStabilityService;
import com.qinhan.service.MemberClusterService;
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
    private final NetworkStabilityService networkStabilityService;

    @Override
    public void updateClusterStatus(ClusterStatus status) {
        // 1. 聚合网络探测结果：LSA 只上报原始探测值，这里做延迟/丢包的统一口径计算
        status = aggregateNetworkMetrics(status);

        // 打印聚合后的 ClusterStatus 详细信息
        log.info("📊 聚合后集群状态 [{}]: 网络延迟={}ms, 丢包率={}%, 对等节点延迟={}",
                status.getClusterName(),
                String.format("%.2f", status.getNetworkLatency()),
                String.format("%.2f", status.getPacketLossRate()),
                status.getPeerLatencyMap());

        // 2. 🔥 核心优化：调用稳定性服务进行评估
        // 语义非常通顺： "请帮我评估一下这个集群的稳定性"
        networkStabilityService.evaluateStability(status);


        // 4. 更新内存缓存
        clusterMap.put(status.getClusterName(), status);

        log.debug("✅ 集群 [{}] 更新完毕: Anomaly={}, Stability={}",
                status.getClusterName(),
                String.format("%.0f", status.getAnomalyScore()),
                String.format("%.0f", status.getStabilityScore()));
    }

    @Override
    public List<ClusterStatus> getAllClusterStatus() {
        return new ArrayList<>(clusterMap.values());
    }


    // 聚合网络探测结果
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