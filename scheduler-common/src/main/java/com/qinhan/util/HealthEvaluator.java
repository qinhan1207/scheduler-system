package com.qinhan.util;

import com.qinhan.model.ClusterStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * 集群健康评估工具类（新版）
 * --------------------------------------------------
 * 基于 Karmada 无法直接采集的扩展指标进行健康评估：
 * - 网络延迟 (networkLatency)
 * - 网络带宽 (networkBandwidth)
 * - 丢包率 (packetLossRate)
 * - 存储使用率 (storageUsage)
 * <p>
 * 打分原则：
 * - 延迟越小越好
 * - 带宽越高越好
 * - 丢包率越低越好
 * - 存储使用率越低越好
 * <p>
 * 输出：
 * - 健康等级（Healthy / Warning / Critical）
 * - 综合健康分数（0 ~ 100）
 */
@Slf4j
public class HealthEvaluator {

    // ===================== 权重配置 =====================
    private static final double W_LATENCY = 0.35;     // 网络延迟权重
    private static final double W_BANDWIDTH = 0.25;   // 网络带宽权重
    private static final double W_LOSS = 0.25;        // 丢包率权重
    private static final double W_STORAGE = 0.15;     // 存储使用率权重


    // ===================== 主要计算方法 =====================

    /**
     * 综合健康评估：返回 "Healthy" / "Warning" / "Critical"
     */
    public static String evaluate(ClusterStatus status) {
        double score = calculateScore(status);

        String level;
        if (score >= 80) {
            level = "Healthy";
        } else if (score >= 60) {
            level = "Warning";
        } else {
            level = "Critical";
        }

        log.debug("🩺 健康评估 => 集群={} | 总分={} | 状态={}",
                status.getClusterName(),
                String.format("%.2f", score),
                level);

        return level;
    }

    /**
     * 计算健康分数（0~100）
     */
    public static double calculateScore(ClusterStatus status) {
        if (status == null) {
            log.warn("⚠️ HealthEvaluator: 收到空的 ClusterStatus，跳过计算。");
            return 0;
        }

        // ---------- 1️⃣ 原始指标 ----------
        double latency = status.getNetworkLatency();      // ms，越小越好
        double bandwidth = status.getNetworkBandwidth();  // Mbps，越大越好
        double loss = status.getPacketLossRate();         // 百分比，越小越好
        double storage = status.getStorageUsage();        // 百分比，越小越好

        // ---------- 2️⃣ 各指标得分（归一化到 0~100） ----------

        // 延迟得分（线性衰减），>100ms 直接视为 0
        double latencyScore = Math.max(0, 100 - latency);

        // 带宽得分（线性比例，200Mbps 为满分）
        double bandwidthScore = Math.min(100, bandwidth / 2.0);

        // 丢包率得分（放大惩罚）
        double lossScore = Math.max(0, 100 - loss * 2000);

        // 存储使用率得分（越低越好）
        double storageScore = Math.max(0, 100 - storage);

        // ---------- 3️⃣ 综合加权 ----------
        double totalScore =
                W_LATENCY * latencyScore +
                        W_BANDWIDTH * bandwidthScore +
                        W_LOSS * lossScore +
                        W_STORAGE * storageScore;

        totalScore = Math.max(0, Math.min(100, totalScore)); // 限制范围

        log.debug(
                "💡 健康打分明细: 延迟={}ms => {} | 带宽={}Mbps => {} | 丢包率={} => {} | 存储={} => {} | 综合={}",
                String.format("%.2f", latency), String.format("%.2f", latencyScore),
                String.format("%.1f", bandwidth), String.format("%.2f", bandwidthScore),
                String.format("%.4f", loss), String.format("%.2f", lossScore),
                String.format("%.1f", storage), String.format("%.2f", storageScore),
                String.format("%.2f", totalScore)
        );


        return totalScore;
    }
}
