package com.qinhan.service;

import com.qinhan.model.ClusterScore;

/**
 * ClusterScoreService
 * 用于根据集群状态计算健康评分，供 Karmada 调度插件调用。
 */
public interface ClusterScoreService {

    /**
     * 根据集群名称计算健康分
     * @param clusterName 集群名称
     * @return ClusterScore 包含健康分和评估理由
     */
    ClusterScore calculateScore(String clusterName);
}