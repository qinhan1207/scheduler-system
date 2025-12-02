package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.properties.LsaClusterConfigProperties;
import com.qinhan.service.ClusterMonitorService;
import com.qinhan.util.K8sClientUtil;
import com.qinhan.util.NetworkUtils; // 核心引入：使用系统级 Ping 工具
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1PodList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ClusterMonitorServiceImpl implements ClusterMonitorService {

    @Autowired
    private LsaClusterConfigProperties lsaProperties;

    private final K8sClientUtil k8sClientUtil;

    private static final double LATENCY_SLA_THRESHOLD = 500.0;
    private static final double MAX_PENALTY_LATENCY = 2000.0;

    public ClusterMonitorServiceImpl(K8sClientUtil k8sClientUtil) {
        this.k8sClientUtil = k8sClientUtil;
    }

    @Override
    public void testClusterConnection(String kubeconfigPath) {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CoreV1Api api = new CoreV1Api(client);
            V1NodeList nodes = api.listNode().execute();
            V1PodList pods = api.listPodForAllNamespaces().execute();
            log.info("✅ 成功连接集群 [{}]: 节点数={}, Pod数={}",
                    kubeconfigPath, nodes.getItems().size(), pods.getItems().size());
        } catch (Exception e) {
            log.error("❌ 无法连接集群 [{}]", kubeconfigPath, e);
        }
    }

    @Override
    public ClusterStatus collectClusterStatus(String kubeconfigPath) {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CoreV1Api api = new CoreV1Api(client);
            V1NodeList nodes = api.listNode().execute();
            V1PodList pods = api.listPodForAllNamespaces().execute();
            int nodeCount = nodes.getItems().size();
            int podCount = pods.getItems().size();
            int pendingPods = (int) pods.getItems().stream()
                    .filter(p -> p.getStatus() != null && "Pending".equalsIgnoreCase(p.getStatus().getPhase()))
                    .count();
            double cpuUsage = Math.min(99.0, podCount * 2.5);
            double memoryUsage = Math.min(99.0, podCount * 3.0);

            // --- 3. 核心网络指标采集 ---
            Map<String, NetworkUtils.NetworkStats> rawStatsMap = probeNeighborsStats();

            Map<String, Double> peerLatencyMap = new HashMap<>();
            double avgLatency = 0.0;
            double reportedPacketLossRate = 0.0; // 上报的自身丢包率

            if (!rawStatsMap.isEmpty()) {
                double validLatencySum = 0.0;
                int validLinkCount = 0;
                double totalRawLossRate = 0.0;

                for (Map.Entry<String, NetworkUtils.NetworkStats> entry : rawStatsMap.entrySet()) {
                    String name = entry.getKey();
                    NetworkUtils.NetworkStats stats = entry.getValue();

                    // 1. 判断链路好坏 (丢包极低 且 延迟达标)
                    boolean isLinkHealthy = (stats.getLossRate() < 0.1) && (stats.getAvgLatency() < LATENCY_SLA_THRESHOLD);

                    // 2. 构建详细 Map (保留坏链路的高延迟信息，供调度器做点对点规避)
                    double displayLatency = stats.getAvgLatency();
                    if (stats.getLossRate() > 0) {
                        displayLatency += stats.getLossRate() * 20.0;
                    }
                    peerLatencyMap.put(name, displayLatency);

                    // 3. 统计健康链路
                    if (isLinkHealthy) {
                        validLatencySum += stats.getAvgLatency();
                        validLinkCount++;
                    }

                    totalRawLossRate += stats.getLossRate();
                }

                // --- 4. 聚合计算 (引入"自证清白"逻辑) ---

                // 计算平均延迟
                if (validLinkCount > 0) {
                    avgLatency = validLatencySum / validLinkCount;
                } else {
                    avgLatency = MAX_PENALTY_LATENCY;
                }

                // 计算丢包率 (Self-Vindication Logic)
                // 只有当连不上任何邻居时，才承认是自己丢包
                if (validLinkCount > 0) {
                    reportedPacketLossRate = 0.0;
                } else {
                    reportedPacketLossRate = totalRawLossRate / rawStatsMap.size();
                }
            }

            return ClusterStatus.builder()
                    .timestamp(Instant.now().toEpochMilli())
                    .apiServerAddress(client.getBasePath())
                    .nodeCount(nodeCount)
                    .podCount(podCount)
                    .pendingPods(pendingPods)
                    .cpuUsage(cpuUsage)
                    .memoryUsage(memoryUsage)
                    .networkLatency(avgLatency)
                    .packetLossRate(reportedPacketLossRate) // 这里的丢包率经过了逻辑修正
                    .peerLatencyMap(peerLatencyMap)
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

        String myName = lsaProperties.getCurrentClusterName(); // 确保你注入了 LsaProperties
        if (myName != null && neighbors.containsKey(myName)) {
            neighbors.remove(myName);
        }
        return neighbors;
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

    /**
     * 兼容旧接口，并确保逻辑一致性
     * 这里的 Latency 也会加上丢包惩罚，避免外部调用时看到的数据不一致
     */
    public Map<String, Double> probeNeighbors() {
        Map<String, Double> result = new HashMap<>();
        probeNeighborsStats().forEach((k, v) -> {
            double penalty = v.getLossRate() * 20.0;
            result.put(k, v.getAvgLatency() + penalty);
        });
        return result;
    }
}