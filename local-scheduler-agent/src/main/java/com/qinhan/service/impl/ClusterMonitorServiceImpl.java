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
import java.util.Map;

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
//            // 🚨 固定值测试 - 消除随机性
//            String clusterName = extractClusterName(kubeconfigPath);
//
//            // 为不同集群设置不同的固定值，便于观察调度逻辑
//            Map<String, Double> fixedCpuMap = Map.of(
//                    "kwok-cluster01", 30.0,
//                    "kwok-cluster02", 60.0,
//                    "cluster01", 40.0,
//                    "cluster02", 50.0,
//                    "cluster03", 70.0,
//                    "cluster04", 20.0,  // 最低，应该被优先选择
//                    "cluster05", 80.0
//            );
//
//            Map<String, Double> fixedMemMap = Map.of(
//                    "kwok-cluster01", 40.0,
//                    "kwok-cluster02", 65.0,
//                    "cluster01", 45.0,
//                    "cluster02", 55.0,
//                    "cluster03", 75.0,
//                    "cluster04", 25.0,  // 最低，应该被优先选择
//                    "cluster05", 85.0
//            );

//            double cpuUsage = fixedCpuMap.getOrDefault(clusterName, 50.0);
//            double memoryUsage = fixedMemMap.getOrDefault(clusterName, 50.0);
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

    /**
     * 从kubeconfig路径提取集群名称（简化版）
     */
//    private String extractClusterName(String kubeconfigPath) {
//        try {
//            // 简单处理：使用文件名或路径名作为集群名
//            java.nio.file.Path path = java.nio.file.Paths.get(kubeconfigPath);
//            String fileName = path.getFileName().toString();
//
//            if (fileName.equals("config") || fileName.equals("kubeconfig")) {
//                // 如果是config文件，使用父目录名
//                return path.getParent().getFileName().toString();
//            } else {
//                // 直接使用文件名（去掉扩展名）
//                return fileName.replace(".config", "").replace("kubeconfig-", "");
//            }
//        } catch (Exception e) {
//            // 如果解析失败，返回路径的hash作为标识
//            return "cluster-" + Math.abs(kubeconfigPath.hashCode() % 1000);
//        }
//    }

}
