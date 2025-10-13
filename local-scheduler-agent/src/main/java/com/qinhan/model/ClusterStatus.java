package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatus {
    private String clusterName;
    private int nodeCount;
    private double cpuUsage;
    private double memoryUsage;
    private long timestamp;
}
