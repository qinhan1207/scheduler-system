//package com.qinhan.util;
//
//import com.qinhan.model.ClusterStatus;
//import lombok.extern.slf4j.Slf4j;
//
///**
// * 集群健康评估工具类
// * --------------------------------------------------
// * 用于计算集群的“常规健康分” (Health Score)。
// * 该分数反映了集群当前的综合承载能力。
// *
// * 评估维度：完全基于网络
// * 1. 网络延迟 (70%): 核心指标，反映跨域通信质量
// * 2. 丢包率 (30%): 核心指标，反映网络稳定性
// *
// * 输出：
// * - 健康等级（Healthy / Warning / Critical）
// * - 综合健康分数（0 ~ 100）
// */
//@Slf4j
//public class HealthEvaluator {
//
//    // ===================== 权重配置 =====================
//    private static final double W_LATENCY = 0.70;     // 网络延迟权重 (70%)
//    private static final double W_LOSS = 0.30;        // 丢包率权重 (30%)
//
//
//    // ===================== 主要计算方法 =====================
//
//    /**
//     * 综合健康评估：返回 "Healthy" / "Warning" / "Critical"
//     */
//    public static String evaluate(ClusterStatus status) {
//        double score = calculateScore(status);
//
//        String level;
//        if (score >= 80) {
//            level = "Healthy"; // 优秀
//        } else if (score >= 50) {
//            level = "Warning"; // 亚健康
//        } else {
//            level = "Critical"; // 严重故障
//        }
//
//        log.debug("🩺 健康评估 => 集群={} | 总分={} | 状态={}",
//                status.getClusterName(),
//                String.format("%.2f", score),
//                level);
//
//        return level;
//    }
//
//    /**
//     * 计算健康分数（0~100）
//     */
//    public static double calculateScore(ClusterStatus status) {
//        if (status == null) {
//            log.warn("⚠️ HealthEvaluator: 收到空的 ClusterStatus，跳过计算。");
//            return 0;
//        }
//
//        // ---------- 1️⃣ 获取指标 ----------
//        double latency = status.getNetworkLatency();      // ms
//        double loss = status.getPacketLossRate();         // %
//
//        // ---------- 2️⃣ 各指标得分计算（归一化到 0~100） ----------
//
//        // (A) 延迟得分：越低越好
//        // 逻辑：0ms=100分，100ms=50分，200ms=0分
//        // 公式：100 - (latency / 2)
//        double latencyScore = Math.max(0, 100 - (latency / 2.0));
//
//        // (B) 丢包率得分：对丢包零容忍
//        // 逻辑：0%=100分，1%=80分，5%=0分
//        // 公式：100 - (loss * 20)
//        double lossScore = Math.max(0, 100 - (loss * 20.0));
//
//        // ---------- 3️⃣ 综合加权 ----------
//        double totalScore =
//                (W_LATENCY * latencyScore) +
//                (W_LOSS * lossScore);
//
//        // 兜底限制 0~100
//        totalScore = Math.max(0, Math.min(100, totalScore));
//
//        log.debug(
//                "💡 打分明细 [{}] : Lat({}ms)={} | Loss({}%)={} => 总分={}",
//                status.getClusterName(),
//                String.format("%.1f", latency), String.format("%.1f", latencyScore),
//                String.format("%.1f", loss),    String.format("%.1f", lossScore),
//                String.format("%.1f", totalScore)
//        );
//
//        return totalScore;
//    }
//}