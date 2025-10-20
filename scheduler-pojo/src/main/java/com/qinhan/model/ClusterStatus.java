package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * ClusterStatus 表示单个成员集群的运行状态信息
 * 由 Local Scheduler Agent (LSA) 周期性上报给 Global Scheduler (GC)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatus {

    /** 集群名称（唯一标识） */
    private String clusterName;

    /** 节点数量 */
    private int nodeCount;

    /** Pod 数量 */
    private int podCount;

    /** CPU 使用率（百分比 0-100） */
    private double cpuUsage;

    /** 内存使用率（百分比 0-100） */
    private double memoryUsage;

    /** 最近上报时间戳 */
    private long timestamp;

    /** 可选字段：备注信息，例如异常原因 */
    private String remark;

    /** 可选字段：集群健康状态 */
    private String healthStatus;

    // 新增字段

    /** 健康分：0-100 越高越好 **/
    private double healthScore;



    public static ClusterStatus simple(String clusterName, int nodeCount, int podCount, double cpu, double mem) {
        ClusterStatus status = new ClusterStatus();
        status.setClusterName(clusterName);
        status.setNodeCount(nodeCount);
        status.setPodCount(podCount);
        status.setCpuUsage(cpu);
        status.setMemoryUsage(mem);
        status.setTimestamp(Instant.now().toEpochMilli());
        status.setHealthStatus(cpu > 90 || mem > 90 ? "Warning" : "Healthy");
        return status;
    }
}
