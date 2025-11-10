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

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.URI;
import java.time.Instant;

@Slf4j
@Service
public class ClusterMonitorServiceImpl implements ClusterMonitorService {

    private final K8sClientUtil k8sClientUtil;

    public ClusterMonitorServiceImpl(K8sClientUtil k8sClientUtil) {
        this.k8sClientUtil = k8sClientUtil;
    }

    /**
     * 测试集群是否能连通
     */
    @Override
    public void testClusterConnection(String kubeconfigPath) {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CoreV1Api api = new CoreV1Api(client);

            // ✅ 新版 API：先创建 Request，再 execute()
            V1NodeList nodes = api.listNode().execute();
            V1PodList pods = api.listPodForAllNamespaces().execute();

            log.info("✅ 成功连接集群 [{}]: 节点数={}, Pod数={}",
                    kubeconfigPath, nodes.getItems().size(), pods.getItems().size());
        } catch (Exception e) {
            log.error("❌ 无法连接集群 [{}]", kubeconfigPath, e);
        }
    }

    /**
     * 收集集群原始数据
     *
     * @param kubeconfigPath 成员集群kubeconfig文件路径
     * @return 集群状态
     */
    @Override
    public ClusterStatus collectClusterStatus(String kubeconfigPath) {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CoreV1Api api = new CoreV1Api(client);

            long startTime = System.currentTimeMillis();

            // ====================== 基础采集 ======================
            V1NodeList nodes = api.listNode().execute();
            V1PodList pods = api.listPodForAllNamespaces().execute();

            int nodeCount = nodes.getItems().size();
            int podCount = pods.getItems().size();


            // ====================== Pod 调度状态统计 ======================
            int pendingPods = (int) pods.getItems().stream()
                    .filter(p -> p.getStatus() != null && "Pending".equalsIgnoreCase(p.getStatus().getPhase()))
                    .count();
            double podPendingRatio = podCount > 0 ? (pendingPods * 100.0 / podCount) : 0.0;

            // ====================== 资源利用率（优化模拟） ======================
            // 让CPU使用率随pod数与pending比例上升
            double cpuBase = 25 + (podCount * 0.05) + (podPendingRatio * 0.1);
            double cpuUsage = Math.min(95, cpuBase + Math.random() * 10 - 5); // 轻微波动 ±5%
            // 让内存与CPU正相关
            double memoryUsage = Math.min(95, cpuUsage + Math.random() * 8 - 4);
            // 让存储随pod数量增长，但增速缓慢
//            double storageUsage = Math.min(90, 10 + podCount * 0.02 + Math.random() * 5);  // 模拟差异化数据，看实验效果
            double storageUsage = 10 + podCount * 0.05 + Math.random() * 10;

            // ====================== 网络指标（模拟） ======================
            double networkLatency = measureNetworkLatency(client.getBasePath());                // 实际为 API 调用延迟
//            double networkBandwidth = 200 / (1 + networkLatency / 2); // 模拟带宽 50~100 Mbps
//            double packetLossRate = networkLatency / 500;       // 模拟丢包率 0~0.1%

            double networkBandwidth = 100 + Math.random() * 100; // 100~200 Mbps
            double packetLossRate = Math.random() * 0.002 + networkLatency / 1000;


            // ====================== 构建 ClusterStatus ======================
            ClusterStatus status = ClusterStatus.builder()
                    .apiServerAddress(client.getBasePath())
                    .nodeCount(nodeCount)
                    .podCount(podCount)
                    .cpuUsage(cpuUsage)
                    .memoryUsage(memoryUsage)
                    .storageUsage(storageUsage)
                    .networkLatency(networkLatency)
                    .networkBandwidth(networkBandwidth)
                    .packetLossRate(packetLossRate)
                    .pendingPods(pendingPods)
                    .podPendingRatio(podPendingRatio)
                    .schedulingQueueLength(pendingPods) // 简单使用 Pending 数代表调度压力
                    .timestamp(Instant.now().toEpochMilli())
                    .build();

            log.info("""
                            📊 集群 [{}] 状态汇总：
                            节点数={}，Pod数={}，
                            CPU={}% MEM={}% Storage={}%，
                            Pending={}({}%)，
                            延迟={}ms，带宽={}Mbps，丢包率={}%
                            """,
                    kubeconfigPath, nodeCount, podCount,
                    String.format("%.1f", cpuUsage), String.format("%.1f", memoryUsage), String.format("%.1f", storageUsage),
                    pendingPods, String.format("%.1f", podPendingRatio),
                    String.format("%.1f", networkLatency), String.format("%.1f", networkBandwidth), String.format("%.3f", packetLossRate)
            );

            return status;

        } catch (Exception e) {
            log.error("❌ 收集集群 [{}] 状态失败: {}", kubeconfigPath, e.getMessage());
            return null;
        }
    }


    /**
     * 测量到目标 API Server 的 TCP 连接平均延迟（毫秒，支持亚毫秒精度）
     */
    private double measureNetworkLatency(String apiServerUrl) {
        try {
            URI uri = new URI(apiServerUrl);
            String host = uri.getHost();
            int port = (uri.getPort() == -1) ? 6443 : uri.getPort(); // 默认 K8s API 端口
            String localHost = InetAddress.getLocalHost().getHostAddress();

            double totalLatency = 0.0;
            int attempts = 5; // 多测几次更稳定

            for (int i = 0; i < attempts; i++) {
                long start = System.nanoTime();
                try (Socket socket = new Socket()) {
                    socket.connect(new InetSocketAddress(host, port), 2000); // 2秒超时
                }
                double elapsedMs = (System.nanoTime() - start) / 1_000_000.0; // 转毫秒（double 保留小数）
                totalLatency += elapsedMs;
                Thread.sleep(100); // 避免过频探测
            }

            double avgLatency = totalLatency / attempts;
            log.info("🌐 网络延迟测量：{} -> {}:{} 平均延迟 = {} ms",
                    localHost, host, port, String.format("%.3f", avgLatency));
            return avgLatency;

        } catch (Exception e) {
            log.warn("⚠️ 无法测量网络延迟 [{}]: {}", apiServerUrl, e.getMessage());
            return 999.0; // 高延迟表示网络不可达
        }
    }


}
