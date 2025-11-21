package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * LSA 模块上报的集群状态信息
 * ClusterStatus 表示单个成员集群的运行状态信息
 * 由 Local Scheduler Agent (LSA) 周期性上报给 Global Scheduler (GC)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ClusterStatus {

    // ================== 基础信息 ==================
    /** 数据库主键 */
    private Long id;

    /** 集群名称（唯一标识） */
    private String clusterName;

    /** 集群 API Server 地址 */
    private String apiServerAddress;

    /** 节点数量 */
    private int nodeCount;

    /** Pod 数量 */
    private int podCount;

    /** 最近上报时间戳 */
    private long timestamp;

    /** 集群健康状态（Healthy / Warning / Critical） */
    private String healthStatus;

    /** 可选字段：备注信息，例如异常原因 */
    private String remark;

    /** 健康分：0-100 越高越好 */
    private double healthScore;


    // ================== 资源利用率 ==================
    /** CPU 使用率（百分比 0-100） */
    private double cpuUsage;

    /** 内存使用率（百分比 0-100） */
    private double memoryUsage;

    /** 存储使用率（百分比 0-100）（原生karmada采集不到） */
    private double storageUsage;


    // ================== 网络指标（原生karmada采集不到） ==================
    /** 平均网络延迟（毫秒） */
    private double networkLatency;

    /** 可用带宽（Mbps） */
    private double networkBandwidth;

    /** 丢包率（百分比 0-100） */
    private double packetLossRate;


    // ================== 调度与负载指标 ==================
    /** Pending Pod 数量 */
    private int pendingPods;

    /** Pending Pod 占比（百分比 0-100） */
    private double podPendingRatio;

    /** 调度队列长度（用于反映当前负载） */
    private double schedulingQueueLength;


    // ================== 异常检测与预测指标 ==================
    /** 异常检测得分（0-1，越高说明越异常）（原生karmada采集不到） */
    private double anomalyScore;

    /** 稳定性预测得分（0-1，越高说明更稳定）（原生karmada采集不到） */
    private double stabilityScore;

    /** 数据记录创建时间（方便时间序分析） */
    private Instant createdAt;


    // ================== 工具方法 ==================
    public static ClusterStatus simple(String clusterName, int nodeCount, int podCount, double cpu, double mem) {
        ClusterStatus status = new ClusterStatus();
        status.setClusterName(clusterName);
        status.setNodeCount(nodeCount);
        status.setPodCount(podCount);
        status.setCpuUsage(cpu);
        status.setMemoryUsage(mem);
        status.setTimestamp(Instant.now().toEpochMilli());
        status.setHealthStatus(cpu > 90 || mem > 90 ? "Warning" : "Healthy");
        status.setHealthScore(Math.max(0, 100 - (cpu + mem) / 2));
        return status;
    }
}
