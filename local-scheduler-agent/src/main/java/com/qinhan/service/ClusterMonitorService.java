package com.qinhan.service;

import com.qinhan.model.ClusterStatus;

/**
 * 负责监控成员集群状态的服务接口
 * 当前仅采集网络可达性与延迟/丢包原始探测结果
 */
public interface ClusterMonitorService {

    /**
     * 测试指定 kubeconfig 是否能连接成功
     */
    void testClusterConnection(String kubeconfigPath);

    /**
     * 采集集群的运行状态（节点数、Pod数、资源使用率）
     */
    ClusterStatus collectClusterStatus(String kubeconfigPath);
}
