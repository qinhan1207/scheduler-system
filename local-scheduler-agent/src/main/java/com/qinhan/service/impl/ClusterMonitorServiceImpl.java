package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ClusterMonitorService;
import com.qinhan.util.K8sClientUtil;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1NodeList;
import io.kubernetes.client.openapi.models.V1PodList;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class ClusterMonitorServiceImpl implements ClusterMonitorService {

    private final K8sClientUtil k8sClientUtil;

    // 模拟邻居列表（后续应该从 Global Scheduler 动态拉取）
    // Key: 集群名, Value: 目标IP:端口 (NodePort)
    // 在 Kind 环境中，不同集群可以通过 Linux 宿主机 IP + NodePort 互通
    private final Map<String, String> peerTargets = new ConcurrentHashMap<>();

    public ClusterMonitorServiceImpl(K8sClientUtil k8sClientUtil) {
        this.k8sClientUtil = k8sClientUtil;
        // 暂时硬编码邻居地址用于测试 (假设 Linux 宿主机 IP 是 10.11.17.222)
        // 如果我是 member1 (30001)，我要测 member2 和 member3
        // 注意：实际部署时，这些信息应该动态获取或配置
        peerTargets.put("member1", "10.11.17.222:30001");
        peerTargets.put("member2", "10.11.17.222:30002");
        peerTargets.put("member3", "10.11.17.222:30003");
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
            // 1. 获取 Client (支持 In-Cluster 模式)
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CoreV1Api api = new CoreV1Api(client);

            // 2. 采集基础资源 (CPU/Mem)
            V1NodeList nodes = api.listNode().execute();
            V1PodList pods = api.listPodForAllNamespaces().execute();

            int nodeCount = nodes.getItems().size();
            int podCount = pods.getItems().size();

            // ... 简单的模拟资源计算逻辑 ...
            // 这里为了演示，保留原有模拟逻辑，实际可改为从 Metrics Server 获取
            int pendingPods = (int) pods.getItems().stream()
                    .filter(p -> p.getStatus() != null && "Pending".equalsIgnoreCase(p.getStatus().getPhase()))
                    .count();
            double podPendingRatio = podCount > 0 ? (pendingPods * 100.0 / podCount) : 0.0;

            double cpuUsage = Math.min(95, 25 + (podCount * 0.05) + (podPendingRatio * 0.1));
            double memoryUsage = Math.min(95, 30 + (podCount * 0.05));
            double storageUsage = 10 + (podCount * 0.05);

            // 3. 采集网络指标 (Ping 邻居) - 这是核心新增逻辑 🔥
            Map<String, Double> latencyMap = probeNeighbors();
            Map<String, Double> lossMap = new HashMap<>(); // 暂时留空，后续可实现丢包率探测

            // 计算平均延迟作为 networkLatency (兼容旧字段)
            double avgLatency = latencyMap.values().stream()
                    .mapToDouble(Double::doubleValue)
                    .average()
                    .orElse(0.0);

            // 模拟带宽和丢包率 (如果无法真实测量)
            double networkBandwidth = 100 + Math.random() * 100;
            double packetLossRate = Math.random() * 0.002 + avgLatency / 1000;

            // 4. 构建 Status
            return ClusterStatus.builder()
                    .apiServerAddress(client.getBasePath())
                    .nodeCount(nodeCount)
                    .podCount(podCount)
                    .cpuUsage(cpuUsage)
                    .memoryUsage(memoryUsage)
                    .storageUsage(storageUsage)
                    .networkLatency(avgLatency) // 存平均值
                    .networkBandwidth(networkBandwidth)
                    .packetLossRate(packetLossRate)
                    .peerLatencyMap(latencyMap) // 存详细矩阵 🔥
                    .peerPacketLossMap(lossMap)
                    .pendingPods(pendingPods)
                    .podPendingRatio(podPendingRatio)
                    .schedulingQueueLength(pendingPods)
                    .timestamp(Instant.now().toEpochMilli())
                    .build();

        } catch (Exception e) {
            log.error("❌ 收集集群状态失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 探测所有邻居的延迟
     */
    public Map<String, Double> probeNeighbors() {
        Map<String, Double> result = new HashMap<>();

        peerTargets.forEach((name, address) -> {
            try {
                String[] parts = address.split(":");
                String host = parts[0];
                int port = Integer.parseInt(parts[1]);

                // 探测 RTT
                double rtt = measureTcpLatency(host, port);
                result.put(name, rtt);
            } catch (Exception e) {
                log.warn("解析目标地址失败: {}", address);
            }
        });

        return result;
    }

    /**
     * 测量 TCP 连接延迟
     * 支持指定 Host 和 Port (不再局限于 API Server)
     */
    private double measureTcpLatency(String host, int port) {
        try {
            double totalLatency = 0.0;
            int attempts = 3; // 测 3 次取平均

            for (int i = 0; i < attempts; i++) {
                long start = System.nanoTime();
                try (Socket socket = new Socket()) {
                    // 2秒超时
                    socket.connect(new InetSocketAddress(host, port), 2000);
                }
                double elapsedMs = (System.nanoTime() - start) / 1_000_000.0;
                totalLatency += elapsedMs;
                // 稍微歇一下，避免过于频繁
                if (i < attempts - 1) Thread.sleep(50);
            }
            return totalLatency / attempts;

        } catch (Exception e) {
            log.debug("⚠️ 无法连接目标 {}:{} - {}", host, port, e.getMessage());
            return 999.0; // 超时/不可达
        }
    }

    // 保留旧方法以兼容
    private double measureNetworkLatency(String apiServerUrl) {
        try {
            URI uri = new URI(apiServerUrl);
            return measureTcpLatency(uri.getHost(), uri.getPort() == -1 ? 6443 : uri.getPort());
        } catch (Exception e) {
            return 999.0;
        }
    }
}