package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 集群状态类
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatus {
    private String clusterName; // 集群名称
    private double cpuUsage;    // cpu利用率
    private double memoryUsage; // 内存利用率
    private int nodeCount;      // 节点数量
    private long timestamp;     // 时间戳
}
