package com.qinhan.service.impl;

import com.qinhan.client.GlobalSchedulerClient;
import com.qinhan.model.ClusterStatus;
import com.qinhan.model.EwmaFeatureVector;
import com.qinhan.properties.LsaClusterConfigProperties;
import com.qinhan.service.ClusterMonitorService;
import com.qinhan.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ClusterMonitorService clusterMonitorService;
    private final LsaClusterConfigProperties lsaProperties;
    private final GlobalSchedulerClient globalSchedulerClient;

    private final Map<String, ClusterStatus> latestStatusMap = new ConcurrentHashMap<>();
    private final Map<String, Deque<EwmaFeatureVector>> ftWindowMap = new ConcurrentHashMap<>();

    /**
     * 高频采样（1s默认）：采样并组装窗口，不上报。
     */
    @Scheduled(fixedRateString = "${lsa.sample-interval-ms:1000}")
    public void sampleAllClusters() {
        String mode = lsaProperties.getMode();
        if ("distributed".equalsIgnoreCase(mode)) {
            sampleSelf();
        } else {
            sampleConfiguredClusters();
        }
    }

    /**
     * 周期上报：低频把最近状态 + f_t窗口上报给 GS。
     */
    @Override
    @Scheduled(fixedRateString = "${lsa.report-interval-ms:5000}")
    public void reportAllClusters() {
        if (latestStatusMap.isEmpty()) {
            log.debug("[上报任务] 暂无采样数据，跳过本周期");
            return;
        }

        latestStatusMap.forEach((clusterName, latest) -> {
            try {
                ClusterStatus payload = buildPayload(clusterName, latest);
                globalSchedulerClient.sendClusterStatus(payload);
                int windowSize = payload.getFtWindow() == null ? 0 : payload.getFtWindow().size();
                log.info("✅ 上报成功: 集群={} | ftWindow={} | mu(latency)={} | mu(loss)={}",
                        clusterName,
                        windowSize,
                        String.format("%.2f", payload.getLatencyMeanFeature()),
                        String.format("%.2f", payload.getLossMeanFeature()));
            } catch (Exception e) {
                log.error("❌ 集群 [{}] 上报失败: {}", clusterName, e.getMessage());
            }
        });
    }

    private void sampleSelf() {
        String clusterName = lsaProperties.getCurrentClusterName();
        log.debug("[采样任务] 分布式采样: {}", clusterName);

        try {
            ClusterStatus status = clusterMonitorService.collectClusterStatus("in-cluster");

            if (status != null) {
                status.setClusterName(clusterName);
                rememberSample(clusterName, status);
            }
        } catch (Exception e) {
            log.error("❌ 本地集群 [{}] 采样失败: {}", clusterName, e.getMessage());
        }
    }

    private void sampleConfiguredClusters() {
        if (lsaProperties.getClusters() == null || lsaProperties.getClusters().getConfigs() == null) {
            log.warn("⚠️ 未配置集群列表");
            return;
        }

        List<LsaClusterConfigProperties.ClusterConfig> configs = lsaProperties.getClusters().getConfigs();

        configs.parallelStream().forEach(config -> {
            try {
                ClusterStatus status = clusterMonitorService.collectClusterStatus(config.getKubeconfigPath());
                if (status != null) {
                    status.setClusterName(config.getName());
                    rememberSample(config.getName(), status);
                }
            } catch (Exception e) {
                log.error("❌ 集群 [{}] 采样失败: {}", config.getName(), e.getMessage());
            }
        });
    }

    private void rememberSample(String clusterName, ClusterStatus status) {
        latestStatusMap.put(clusterName, status);

        Deque<EwmaFeatureVector> queue = ftWindowMap.computeIfAbsent(clusterName, k -> new ArrayDeque<>());
        synchronized (queue) {
            queue.addLast(EwmaFeatureVector.builder()
                    .meanFeature(status.getLatencyMeanFeature())
                    .deviationFeature(status.getLatencyDeviationFeature())
                    .volatility(status.getVolatility())
                    .build());

            int maxWindow = Math.max(1, lsaProperties.getFtWindowSize());
            while (queue.size() > maxWindow) {
                queue.removeFirst();
            }
        }
    }

    private ClusterStatus buildPayload(String clusterName, ClusterStatus latest) {
        List<EwmaFeatureVector> ftWindow;
        Deque<EwmaFeatureVector> queue = ftWindowMap.get(clusterName);
        if (queue == null) {
            ftWindow = new ArrayList<>();
        } else {
            synchronized (queue) {
                ftWindow = new ArrayList<>(queue);
            }
        }

        return ClusterStatus.builder()
                .clusterName(clusterName)
                .timestamp(latest.getTimestamp())
                .networkLatency(latest.getNetworkLatency())
                .packetLossRate(latest.getPacketLossRate())
                .peerRawStats(latest.getPeerRawStats())
                .latencyMeanFeature(latest.getLatencyMeanFeature())
                .lossMeanFeature(latest.getLossMeanFeature())
                .latencyDeviationFeature(latest.getLatencyDeviationFeature())
                .volatility(latest.getVolatility())
                .ftWindow(ftWindow)
                .build();
    }
}