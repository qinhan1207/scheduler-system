package com.qinhan.service;

import com.qinhan.model.ClusterStatus;

/**
 * 异常检测服务接口
 * ------------------------------------------------------------
 * 用于封装异常分计算与状态判定逻辑。
 * 后续可扩展为批量检测、存储入库等功能。
 */
public interface AnomalyDetectionService {

    /**
     * 对单个集群进行异常检测
     * @param status 当前集群状态
     */
    void detectClusterAnomaly(ClusterStatus status);
}
