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
     * 收集集群运行状态
     */
    @Override
    public ClusterStatus collectClusterStatus(String kubeconfigPath) {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CoreV1Api api = new CoreV1Api(client);

            V1NodeList nodes = api.listNode().execute();
            V1PodList pods = api.listPodForAllNamespaces().execute();


            int nodeCount = nodes.getItems().size();
            int podCount = pods.getItems().size();

            // TODO: 如果 metrics-server 存在，可进一步采集 CPU/内存
            double cpuUsage = Math.random() * 100;      // 临时随机值
            double memoryUsage = Math.random() * 100;   // 临时随机值

            ClusterStatus status = ClusterStatus.builder()
                    .clusterName(kubeconfigPath)
                    .nodeCount(nodeCount)
                    .podCount(podCount)
                    .cpuUsage(cpuUsage)
                    .memoryUsage(memoryUsage)
                    .timestamp(Instant.now().toEpochMilli()).build();

            log.info("📊 集群 [{}] 状态: 节点={}, Pods={}, CPU={}% MEM={}%",
                    kubeconfigPath, nodeCount, podCount,
                    String.format("%.2f", cpuUsage),
                    String.format("%.2f", memoryUsage));

            return status;
        } catch (Exception e) {
            log.error("❌ 收集集群 [{}] 状态失败: {}", kubeconfigPath, e.getMessage());
            return null;
        }
    }

}
