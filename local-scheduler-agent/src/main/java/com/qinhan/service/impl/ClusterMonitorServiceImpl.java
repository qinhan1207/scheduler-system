package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.RawNetworkStats;
import com.qinhan.properties.LsaClusterConfigProperties;
import com.qinhan.service.ClusterMonitorService;
import com.qinhan.util.K8sClientUtil;
import com.qinhan.util.NetworkUtils; // 核心引入：使用系统级 Ping 工具
import io.kubernetes.client.openapi.ApiClient;
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

    public ClusterMonitorServiceImpl(K8sClientUtil k8sClientUtil) {
        this.k8sClientUtil = k8sClientUtil;
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
            // --- 3. 核心网络指标采集 ---
            Map<String, NetworkUtils.NetworkStats> rawStatsMap = probeNeighborsStats();
            Map<String, RawNetworkStats> peerRawStats = new HashMap<>();
            rawStatsMap.forEach((name, stats) -> peerRawStats.put(name,
                    RawNetworkStats.builder()
                            .avgLatency(stats.getAvgLatency())
                            .lossRate(stats.getLossRate())
                            .build()));

            return ClusterStatus.builder()
                    .timestamp(Instant.now().toEpochMilli())
                    .apiServerAddress(client.getBasePath())
                    .peerRawStats(peerRawStats)
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


}