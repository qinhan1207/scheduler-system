package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.Map;

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

    // ================== 身份信息 ==================
    /** 数据库主键 (入库后生成) */
    private Long id;

    /** 集群名称（唯一标识） */
    private String clusterName;

    /** 集群 API Server 地址 */
    private String apiServerAddress;

    /** Agent 采集时的各时间戳 */
    private long timestamp;

    // ================== 核心网络指标 (本课题创新点) ==================
    /** 平均网络延迟 (ms) - 用于快速筛选 */
    private double networkLatency;

    /** 丢包率 (%) - 反映网络稳定性 */
    private double packetLossRate;

    /** 原始探测结果：到各邻居的 RTT/丢包率 */
    private Map<String, RawNetworkStats> peerRawStats;

    /** * 🔥 全域感知矩阵：我到其他集群的 RTT 延迟
     * Key: 目标集群名称 (e.g., "member-2"), Value: RTT (ms)
     * 调度器将利用此 Map 计算亲和性距离
     */
    private Map<String, Double> peerLatencyMap;

    // ================== 异常检测与预测结果 (由 GS 计算回填) ==================
    /** 异常检测得分（0-100） */
    private double anomalyScore;

    /** 稳定性预测得分（EWMA 预测结果） */
    private double stabilityScore;

    /** 集群健康状态（Healthy / Warning / Critical） */
    private String healthStatus;

    /** 健康分：0-100 越高越好 */
    private double healthScore;

    /** 备注信息 */
    private String remark;

    /** 数据入库时间 (服务端生成) */
    private Instant createdAt;

}