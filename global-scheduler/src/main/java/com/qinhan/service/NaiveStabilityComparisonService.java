package com.qinhan.service;

import com.qinhan.model.ClusterStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;

/**
 * NaiveStabilityComparisonService
 * 影子对比服务 (Final Version: 模拟聚合窗口带来的观测偏差)
 * * 核心逻辑：
 * 1. Native K8s/Prometheus 通常使用滑动窗口平均 (e.g., avg_over_time[15s])。
 * 2. 本服务维护一个长度为 15 的队列，模拟这种"对历史数据一视同仁"的算法。
 * 3. 结果：当故障突发时，Native 评分会滞后 (Lag)，而 RCGS (EWMA) 会瞬间响应。
 */
@Slf4j
@Component("naiveStabilityComparisonService")
public class NaiveStabilityComparisonService {

    // 归一化基准 (与主服务保持一致)
    private static final double MAX_LATENCY_NORM = 300.0;

    // 模拟原生监控的聚合窗口大小 (15个点 ≈ 15秒)
    // 论文 Argument: 原生监控为了抗抖动，必须聚合，导致了 15s 的观测盲区
    private static final int AGGREGATION_WINDOW_SIZE = 15;

    // 存储每个集群的历史数据窗口 (内存缓存)
    private final Map<String, Queue<Double>> historyWindows = new ConcurrentHashMap<>();

    public void logComparison(ClusterStatus status) {
        String clusterName = status.getClusterName();
        double currentRawLatency = status.getNetworkLatency();

        // ==========================================
        // 1. 模拟原生聚合计算 (The Aggregation Bias)
        // ==========================================

        // 获取该集群的历史窗口
        Queue<Double> window = historyWindows.computeIfAbsent(clusterName, k -> new LinkedList<>());

        // 加入最新数据
        window.offer(currentRawLatency);

        // 维持窗口大小 (模拟 15s 滚动窗口)
        if (window.size() > AGGREGATION_WINDOW_SIZE) {
            window.poll();
        }

        // 计算窗口内的算术平均值 (Native 看到的"旧"数据)
        // 这里体现了 SMA (Simple Moving Average) 的滞后性
        double aggregatedLatency = window.stream()
                .mapToDouble(Double::doubleValue)
                .average()
                .orElse(currentRawLatency);

        // ==========================================
        // 2. 计算 Naive 分数 (基于聚合值)
        // ==========================================

        // Native 算法使用的是"迟钝"的 aggregatedLatency
        double normAggregated = Math.min(1.0, aggregatedLatency / MAX_LATENCY_NORM);
        double cost = normAggregated;

        // 简单的线性映射 (无风险惩罚)
        double naiveScore = (1.0 - cost) * 100.0;
        naiveScore = Math.max(0.0, Math.min(100.0, naiveScore));

        // ==========================================
        // 3. 打印核心对比日志 (Evidence Chain)
        // ==========================================

        // 获取你的算法算出的分数 (基于 EWMA + LSTM Risk)
        double proposedScore = status.getStabilityScore();

        // 日志解释：
        // Real: 真实瞬时值 (例如 116ms)
        // NativeView: 朴素视角 (例如 25ms, 因为被前14秒的好数据稀释了)
        // NaiveScore: 朴素打分 (例如 91分 -> 导致调度错误)
        // RCGS_Score: 你的打分 (例如 30分 -> 正确避坑)
        log.info("⚖️ [DECISION_EVIDENCE] Cluster={} | Real:{}ms | NativeView(Avg15s):{}ms -> NaiveScore:{} (Lag) vs RCGS_Score:{}",
                clusterName,
                String.format("%.0f", currentRawLatency),
                String.format("%.1f", aggregatedLatency),
                String.format("%.1f", naiveScore),
                String.format("%.1f", proposedScore)
        );
    }
}