package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
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
    /** 集群名称（唯一标识） */
    private String clusterName;

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

    // ================== EWMA 特征结果 (由 LSA 计算上报) ==================
    /** EWMA 延迟均值特征 (mu_latency) */
    private double latencyMeanFeature;

    /** EWMA 丢包均值特征 (mu_loss) */
    private double lossMeanFeature;

    /**
     * EWMA 延迟偏差特征 (sigma_t)
     * 来源：EwmaFeatureExtractor 中的 newDeviation。
     */
    private double latencyDeviationFeature;

    // ================== 稳定性评分结果 (由 GS 计算回填) ==================
    /** 稳定性预测得分（EWMA 预测结果） */
    private double stabilityScore;

    // ========================== 🆕 新增字段 ==========================
    /**
     * 波动率 (Volatility / Sigma)
     * 定义：| 当前真实值 - EWMA预测值 |
     * 含义：反映网络的“抖动”程度。值越大，说明网络越不稳定，预测越困难。
     * 作用：作为 StabilityScore 的扣分依据之一。
     */
    private double volatility;

    /** 备注信息 */
    private String remark;

    /**
     * 特征窗口序列 f_t (由 LSA 侧组装)
      * 约定：meanFeature=延迟均值(mu), deviationFeature=延迟偏差(sigma), volatility=sigma/mu
     */
    private List<EwmaFeatureVector> ftWindow;

}