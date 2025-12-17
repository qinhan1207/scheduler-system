package com.qinhan.service;

import com.qinhan.model.ClusterStatus;

/**
 * 资源补全已废弃，保留接口以兼容旧调用。
 */
public interface ResourceSimulatorService {

    /**
     * 直接透传集群状态。
     */
    ClusterStatus enrichDynamicMetrics(ClusterStatus rawStatus);
}
