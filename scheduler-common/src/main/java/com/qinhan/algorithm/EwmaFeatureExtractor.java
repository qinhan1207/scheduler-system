package com.qinhan.algorithm;

import com.qinhan.model.EwmaState;
import com.qinhan.model.EwmaFeatureVector;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EWMA 特征提取器 (边缘侧 LSA 专用)
 * 职责：执行时序平滑并提取 f_t = <mu, sigma, volatility>
 */
@Slf4j
@Component
public class EwmaFeatureExtractor {

    // 状态容器：Key = "clusterName:metricName"
    private final Map<String, EwmaState> stateMap = new ConcurrentHashMap<>();

    // 基础平滑因子 (超参数)
    private static final double BASE_ALPHA = 0.3;
    private static final double BETA = 0.1;

    /**
     * 核心特征提取方法
     *
     * @param clusterName  集群名
     * @param metricName   指标名 (e.g., "latency", "loss")
     * @param currentValue 当前观测值
     * @return EWMA 单步特征 f_t
     */
    public EwmaFeatureVector extractFeatures(String clusterName, String metricName, double currentValue) {
        String key = clusterName + ":" + metricName;

        EwmaState state = stateMap.computeIfAbsent(key, k -> new EwmaState());

        if (!state.isInitialized()) {
            state.setMean(currentValue);
            state.setDeviation(0.0);
            state.setInitialized(true);
            return EwmaFeatureVector.builder()
                    .meanFeature(currentValue)
                    .deviationFeature(0.0)
                    .volatility(0.0)
                    .build();
        }

        double lastMean = state.getMean();
        double error = Math.abs(currentValue - lastMean);
        double adaptiveAlpha = calculateAdaptiveAlpha(error, lastMean);

        double newDeviation = BETA * error + (1 - BETA) * state.getDeviation();
        double newMean = adaptiveAlpha * currentValue + (1 - adaptiveAlpha) * lastMean;

        state.setMean(newMean);
        state.setDeviation(newDeviation);

        double volatility = (newMean > 0.0001) ? (newDeviation / newMean) : 0.0;

        String unit = metricName.equals("latency") ? "ms" : "%";
        log.debug("[EWMA特征] 集群=[{}] 指标=[{}] 观测:{}{} -> mu:{}{} sigma:{} v:{}% alpha:{}",
                clusterName,
                metricName,
                String.format("%6.2f", currentValue),
                unit,
                String.format("%6.2f", newMean),
                unit,
                String.format("%.2f", newDeviation),
                String.format("%.1f", volatility * 100),
                String.format("%.2f", adaptiveAlpha)
        );

        return EwmaFeatureVector.builder()
                .meanFeature(newMean)
                .deviationFeature(newDeviation)
                .volatility(volatility)
                .build();
    }

    private double calculateAdaptiveAlpha(double error, double lastValue) {
        double errorRatio = (Math.abs(lastValue) < 0.0001) ? 0 : error / Math.abs(lastValue);
        double alpha = BASE_ALPHA + (errorRatio * 0.5);
        return Math.max(0.1, Math.min(0.8, alpha));
    }
}
