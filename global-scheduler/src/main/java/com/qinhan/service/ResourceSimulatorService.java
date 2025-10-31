package com.qinhan.service;

import com.qinhan.model.ClusterStatus;

/**
 * 模拟资源使用率（CPU / 内存）的接口
 */
public interface ResourceSimulatorService {

    /**
     * 补全缺失的动态指标（如 CPU、内存使用率）
     * @param rawStatus 来自 LSA 的原始集群状态
     * @return 带有补全指标的 ClusterStatus
     */
    ClusterStatus enrichDynamicMetrics(ClusterStatus rawStatus);
}
