package com.qinhan.service.impl;

import com.qinhan.algorithm.EwmaFeatureExtractor;
import com.qinhan.model.ClusterStatus;
import com.qinhan.model.EwmaFeatureVector;
import com.qinhan.model.RawNetworkStats;
import com.qinhan.properties.LsaClusterConfigProperties;
import com.qinhan.service.ClusterMonitorService;
import com.qinhan.util.K8sClientUtil;
import com.qinhan.util.NetworkUtils; // 核心引入：使用系统级 Ping 工具
import io.kubernetes.client.openapi.ApiClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ClusterMonitorServiceImpl implements ClusterMonitorService {

    @Resource
    private LsaClusterConfigProperties lsaProperties;

    private final K8sClientUtil k8sClientUtil;
    private final EwmaFeatureExtractor ewmaFeatureExtractor;

    public ClusterMonitorServiceImpl(K8sClientUtil k8sClientUtil, EwmaFeatureExtractor ewmaFeatureExtractor) {
        this.k8sClientUtil = k8sClientUtil;
        this.ewmaFeatureExtractor = ewmaFeatureExtractor;
    }


    @Override
    public void testClusterConnection(String kubeconfigPath) {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            log.info("✅ 成功连接集群 [{}]", kubeconfigPath);
        } catch (Exception e) {
            log.error("❌ 无法连接集群 [{}]", kubeconfigPath, e);
        }
    }

    @Override
    public ClusterStatus collectClusterStatus(String kubeconfigPath) {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            // 仅初始化 client，后续只关心网络探测
            
            // === 步骤1：采集原始网络探测数据 ===
            Map<String, NetworkUtils.NetworkStats> rawStatsMap = probeNeighborsStats();
            Map<String, RawNetworkStats> peerRawStats = new HashMap<>();
            rawStatsMap.forEach((name, stats) -> peerRawStats.put(name,
                    RawNetworkStats.builder()
                            .avgLatency(stats.getAvgLatency())
                            .lossRate(stats.getLossRate())
                            .build()));

            // === 步骤2：聚合网络指标（当前值） ===
            double networkLatency = aggregateNetworkLatency(peerRawStats);
            double packetLossRate = aggregatePacketLoss(peerRawStats);
            
                // === 步骤3：EWMA 特征提取（边缘侧） ===
            String clusterName = lsaProperties.getCurrentClusterName();
                EwmaFeatureVector latencyFeature = ewmaFeatureExtractor.extractFeatures(clusterName, "latency", networkLatency);
                EwmaFeatureVector lossFeature = ewmaFeatureExtractor.extractFeatures(clusterName, "loss", packetLossRate);
            
                log.info("✅ [EWMA特征] 集群={} | latency(mu={}, sigma={}) | loss(mu={}, sigma={})",
                    clusterName,
                    String.format("%.2f", latencyFeature.getMeanFeature()),
                    String.format("%.2f", latencyFeature.getDeviationFeature()),
                    String.format("%.2f", lossFeature.getMeanFeature()),
                    String.format("%.2f", lossFeature.getDeviationFeature()));

            return ClusterStatus.builder()
                    .timestamp(Instant.now().toEpochMilli())
                    .peerRawStats(peerRawStats)
                    // 聚合后的当前值
                    .networkLatency(networkLatency)
                    .packetLossRate(packetLossRate)
                    // EWMA 特征值
                    .latencyMeanFeature(latencyFeature.getMeanFeature())
                    .lossMeanFeature(lossFeature.getMeanFeature())
                    .latencyDeviationFeature(latencyFeature.getDeviationFeature())
                    .volatility(latencyFeature.getVolatility())
                    .build();

        } catch (Exception e) {
            log.error("❌ 收集集群状态失败: {}", e.getMessage());
            return null;
        }
    }

    private Map<String, String> getNeighborIps() {
        Map<String, String> neighbors = new HashMap<>();
        neighbors.put("member1", "member1-control-plane");
        neighbors.put("member2", "member2-control-plane");
        neighbors.put("member3", "member3-control-plane");
        neighbors.put("member4", "member4-control-plane");
        neighbors.put("member5", "member5-control-plane");

        String myName = lsaProperties.getCurrentClusterName(); // 确保你注入了 LsaProperties
        if (myName != null && neighbors.containsKey(myName)) {
            neighbors.remove(myName);
        }
        return neighbors;
    }


    /**
     * 聚合网络延迟（当前值）
     * 策略：取当前集群到其他成员集群的“最小时延”作为本时刻的集群级延迟代理值。
     * 若所有观测都无效，则返回惩罚值。
     */
    private double aggregateNetworkLatency(Map<String, RawNetworkStats> rawMap) {
        final double PENALTY_LATENCY = 2000.0;   // 无有效数据时的惩罚值
        final double VALID_MAX_LATENCY = 5000.0; // 过滤明显异常的无效大值上限

        if (rawMap == null || rawMap.isEmpty()) {
            return PENALTY_LATENCY;
        }

        double minLatency = Double.MAX_VALUE;
        boolean hasValid = false;

        for (RawNetworkStats stats : rawMap.values()) {
            if (stats == null) {
                continue;
            }

            double latency = stats.getAvgLatency();

            // 过滤无效值：NaN、非正数、异常过大值
            if (Double.isNaN(latency) || latency <= 0 || latency >= VALID_MAX_LATENCY) {
                continue;
            }

            // 对极端值进行裁剪，避免异常尖峰直接穿透
            latency = Math.min(latency, PENALTY_LATENCY);

            if (latency < minLatency) {
                minLatency = latency;
                hasValid = true;
            }
        }

        return hasValid ? minLatency : PENALTY_LATENCY;
    }
    
    /**
     * 聚合丢包率（当前值）
     */
    private double aggregatePacketLoss(Map<String, RawNetworkStats> rawMap) {
        if (rawMap == null || rawMap.isEmpty()) {
            return 100.0; // 无数据时的惩罚值
        }
        
        double healthyCount = 0;
        for (RawNetworkStats stats : rawMap.values()) {
            if (stats.getLossRate() < 0.1 && stats.getAvgLatency() < 500) {
                healthyCount++;
            }
        }
        
        // 有健康链路则丢包率记为0，否则计算平均
        if (healthyCount > 0) {
            return 0.0;
        }
        
        double totalLoss = 0.0;
        for (RawNetworkStats stats : rawMap.values()) {
            totalLoss += stats.getLossRate();
        }
        return rawMap.size() > 0 ? totalLoss / rawMap.size() : 100.0;
    }

    public Map<String, NetworkUtils.NetworkStats> probeNeighborsStats() {
        Map<String, NetworkUtils.NetworkStats> result = new ConcurrentHashMap<>();
        Map<String, String> neighbors = getNeighborIps();

        neighbors.entrySet().parallelStream().forEach(entry -> {
            String name = entry.getKey();
            String target = entry.getValue();
            NetworkUtils.NetworkStats stats = NetworkUtils.ping(target, 5, 2);
            result.put(name, stats);
            // 减少日志噪音，只有真正有问题才打印
            if (stats.getLossRate() > 0) {
                log.info("⚠️ [Probe] Cluster={} Target={} Loss={}%, Lat={}ms",
                        name, target, stats.getLossRate(), stats.getAvgLatency());
            }
        });
        return result;
    }


}