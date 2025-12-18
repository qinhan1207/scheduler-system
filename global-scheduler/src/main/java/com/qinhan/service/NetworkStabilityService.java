package com.qinhan.service;

import com.qinhan.model.ClusterStatus;

/**
 * 网络稳定性分析服务
 * 对应论文 Section 3.3: Intelligent Analysis Layer
 * 职责：负责 EWMA 预测、波动率计算以及基于论文公式的 S_net 评分
 */
public interface NetworkStabilityService {

    /**
     * 评估集群的网络稳定性
     * @param status 聚合后的成员集群指标
     */
    void evaluateStability(ClusterStatus status);
}
