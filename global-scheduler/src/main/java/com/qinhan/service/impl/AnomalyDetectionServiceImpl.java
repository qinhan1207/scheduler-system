package com.qinhan.service.impl;

import com.qinhan.algorithm.EwmaForecaster;
import com.qinhan.model.ClusterStatus;
import com.qinhan.service.AnomalyDetectionService;
import com.qinhan.util.AnomalyDetectorUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    private final EwmaForecaster ewmaForecaster;

    // 预测阈值：如果预测未来延迟超过 150ms，视为高风险
    private static final double PREDICTED_LATENCY_THRESHOLD = 150.0;
    // 预测阈值：如果预测未来丢包率超过 1%，视为高风险
    private static final double PREDICTED_LOSS_THRESHOLD = 1.0;

    @Override
    public void detectClusterAnomaly(ClusterStatus status) {
        String clusterName = status.getClusterName();

        // 1. 计算静态异常分 (基于当前值) - 用于记录现状
        double staticAnomalyScore = AnomalyDetectorUtil.calculateAnomalyScore(status);
        status.setAnomalyScore(staticAnomalyScore);

        // 2. 🔥 核心：执行 EWMA 时序预测 (预测未来)
        // 我们只预测两个核心网络指标，不再关注带宽和存储
        double predictedLatency = ewmaForecaster.predict(clusterName, "latency", status.getNetworkLatency());
        double predictedLoss = ewmaForecaster.predict(clusterName, "loss", status.getPacketLossRate());

        // 3. 计算稳定性得分 (Stability Score) - 初始 100 分
        // 这个分数反映了“未来一小段时间内该集群保持健康的概率”
        double stabilityScore = 100.0;

        // 规则 A: 如果预测延迟过高，重罚
        if (predictedLatency > PREDICTED_LATENCY_THRESHOLD) {
            // 延迟越高扣分越狠，每超 10ms 多扣 5 分
            double over = predictedLatency - PREDICTED_LATENCY_THRESHOLD;
            stabilityScore -= (20 + (over / 10.0) * 5);
        }

        // 规则 B: 如果预测丢包，直接熔断式扣分
        if (predictedLoss > PREDICTED_LOSS_THRESHOLD) {
            // 丢包对业务影响最大，直接扣 50 分起步
            stabilityScore -= 50;
        }

        // 规则 C: 结合静态异常分微调 (防止预测模型对于突发情况反应过度或不足)
        if (staticAnomalyScore > 60) {
            stabilityScore -= 10;
        }

        // 兜底限制 0~100
        stabilityScore = Math.max(0, Math.min(100, stabilityScore));
        status.setStabilityScore(stabilityScore);

        // 4. 生成备注 (方便调试和论文截图)
        String remark = String.format("Pred: Lat=%.0fms, Loss=%.1f%% | Stability=%.0f",
                predictedLatency, predictedLoss, stabilityScore);
        status.setRemark(remark);


        log.info("🧠 预测分析 [{}] -> 真实Lat:{}ms | 预测Lat:{}ms | 稳定性得分:{}",
                clusterName,
                String.format("%.2f", status.getNetworkLatency()), // 改成 .2f
                String.format("%.2f", predictedLatency),           // 改成 .2f
                String.format("%.1f", stabilityScore));
    }
}