package com.qinhan.algorithm;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EWMA (Exponential Weighted Moving Average) 时序预测器
 * 对应开题报告中的 "基于轻量级时序预测的主动式异常熔断算法"
 *
 */
@Slf4j
@Component
public class EwmaForecaster {

    // 存储每个集群、每个指标的上一次预测值 (Smoothed Value)
    // Key: "clusterName:metricName" -> Value: double
    private final Map<String, Double> historyMap = new ConcurrentHashMap<>();

    // 基础平滑因子 (0 < alpha < 1)
    // 较小的值(如0.2)使平滑更强，对噪声不敏感；较大的值(如0.8)对突变反应更快
    // 如果 alpha 很大（接近 1）： 预测值主要由“当前真实值”决定。特点是反应快，但抗干扰差（容易被抖动误导）。
    // 如果 alpha 很小（接近 0）： 预测值主要由“历史记忆”决定。特点是非常平稳，但反应迟钝（网络真坏了半天才反应过来）。
    private static final double BASE_ALPHA = 0.3;

    /**
     * 执行预测并更新历史状态
     *
     * @param clusterName 集群名称
     * @param metricName  指标名称 (e.g., "latency", "cpu")
     * @param currentValue 当前采集到的真实值
     * @return 预测的下一时刻值
     */
    public double predict(String clusterName, String metricName, double currentValue) {
        String key = clusterName + ":" + metricName;
        Double lastForecast = historyMap.get(key);

        // 1. 如果是第一次数据，无法预测，直接初始化
        if (lastForecast == null) {
            historyMap.put(key, currentValue);
            return currentValue;
        }

        // 2. 计算预测误差 (Error)
        double error = Math.abs(currentValue - lastForecast);

        // 3. 自适应 Alpha 调节机制 (论文创新点)
        // 如果误差很大，说明网络发生了突变，我们需要增大 Alpha，让模型快速“跟上”新趋势
        // 如果误差很小，说明网络平稳，减小 Alpha，过滤噪声
        double adaptiveAlpha = calculateAdaptiveAlpha(error, lastForecast);

        // 4. EWMA 公式: St = α * Yt + (1 - α) * St-1
        // newForecast = alpha * current + (1 - alpha) * last
        double newForecast = adaptiveAlpha * currentValue + (1 - adaptiveAlpha) * lastForecast;

        // 更新历史
        historyMap.put(key, newForecast);

        log.debug("🔮 EWMA预测 [{}] {} -> 真实值:{} | 预测值:{} | 误差:{} | Alpha:{}",
                clusterName, metricName,
                String.format("%.2f", currentValue),
                String.format("%.2f", newForecast),
                String.format("%.2f", error),
                String.format("%.2f", adaptiveAlpha));

        return newForecast;
    }

    /**
     * 根据误差动态计算 Alpha
     */
    private double calculateAdaptiveAlpha(double error, double lastValue) {
        // 相对误差率
        double errorRatio = (lastValue == 0) ? 0 : error / Math.abs(lastValue);

        // 简单的自适应策略：
        // 基础 alpha = 0.3
        // 加上误差率带来的增益，最大限制在 0.8，最小 0.1
        double alpha = BASE_ALPHA + (errorRatio * 0.5);
        return Math.max(0.1, Math.min(0.8, alpha));
    }
}