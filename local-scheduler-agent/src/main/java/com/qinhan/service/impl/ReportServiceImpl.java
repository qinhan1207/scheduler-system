package com.qinhan.service.impl;

import com.qinhan.client.GlobalSchedulerClient;
import com.qinhan.model.ClusterStatus;
import com.qinhan.properties.LsaClusterConfigProperties;
import com.qinhan.service.ClusterMonitorService;
import com.qinhan.service.ReportService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportServiceImpl implements ReportService {

    private final ClusterMonitorService clusterMonitorService;
    private final LsaClusterConfigProperties lsaProperties;
    private final GlobalSchedulerClient globalSchedulerClient;

    /**
     * 定时采集并上报
     * 频率: 每 10 秒一次
     */
    @Override
    @Scheduled(fixedRate = 10000)
    public void reportAllClusters() {
        String mode = lsaProperties.getMode();

        if ("distributed".equalsIgnoreCase(mode)) {
            // === 分布式模式 (In-Cluster) ===
            reportSelf();
        } else {
            // === 集中式模式 (Standalone / Dev) ===
            reportConfiguredClusters();
        }
    }

    /**
     * 分布式模式：只采集自己
     */
    private void reportSelf() {
        String clusterName = lsaProperties.getCurrentClusterName();
        log.info("🛰️ [分布式模式] 开始采集本地集群状态: {}", clusterName);

        try {
            // 传 "in-cluster" 给 ClientUtil，让它使用 ServiceAccount
            ClusterStatus status = clusterMonitorService.collectClusterStatus("in-cluster");

            if (status != null) {
                status.setClusterName(clusterName); // 补全名字

                // 上报
                globalSchedulerClient.sendClusterStatus(status);

                int neighborCount = (status.getPeerRawStats() != null) ? status.getPeerRawStats().size() : 0;
                log.info("✅ 上报成功: 集群={} | 探测邻居数={} | 原始探测样本={}",
                        clusterName,
                        neighborCount,
                        neighborCount > 0 ? status.getPeerRawStats() : "none");
            }
        } catch (Exception e) {
            log.error("❌ 本地集群 [{}] 上报失败: {}", clusterName, e.getMessage());
        }
    }

    /**
     * 集中式模式：遍历配置文件 (旧逻辑保留用于调试)
     */
    private void reportConfiguredClusters() {
        log.info("🛰️ [集中式模式] 开始轮询采集配置列表...");

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
                    globalSchedulerClient.sendClusterStatus(status);
                    log.info("✅ 上报成功: {}", config.getName());
                }
            } catch (Exception e) {
                log.error("❌ 集群 [{}] 上报失败: {}", config.getName(), e.getMessage());
            }
        });
    }
}