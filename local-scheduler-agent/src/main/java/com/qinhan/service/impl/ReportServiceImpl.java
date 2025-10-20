package com.qinhan.service.impl;

import com.qinhan.client.GlobalSchedulerClient;
import com.qinhan.model.ClusterStatus;
import com.qinhan.properties.ClusterProperties;
import com.qinhan.service.ClusterMonitorService;
import com.qinhan.service.ReportService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    private final ClusterMonitorService clusterMonitorService;
    private final ClusterProperties clusterProperties;
    private final GlobalSchedulerClient globalSchedulerClient;

    public ReportServiceImpl(ClusterMonitorService clusterMonitorService,
                             ClusterProperties clusterProperties,
                             GlobalSchedulerClient globalSchedulerClient) {
        this.clusterMonitorService = clusterMonitorService;
        this.clusterProperties = clusterProperties;
        this.globalSchedulerClient = globalSchedulerClient;
    }

    /**
     * 每10秒采集并上报所有集群状态
     */
    @Override
    @Scheduled(fixedRate = 10000)
    public void reportAllClusters() {
        log.info("🛰️ 开始采集并上报所有集群状态...");

        clusterProperties.getConfigs().parallelStream().forEach(config -> {
            try {
                ClusterStatus status = clusterMonitorService.collectClusterStatus(config.getKubeconfigPath());
                if (status != null) {
                    status.setClusterName(config.getName());
                    globalSchedulerClient.sendClusterStatus(status);
                    log.info("✅ 上报成功: 集群={} 节点={} Pods={}", config.getName(), status.getNodeCount(), status.getPodCount());
                }
            } catch (Exception e) {
                log.error("❌ 集群 [{}] 上报失败: {}", config.getName(), e.getMessage());
            }
        });

        log.info("📡 全部集群状态上报完成");
    }
}
