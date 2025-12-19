package com.qinhan.service.impl;

import com.qinhan.algorithm.EwmaForecaster;
import com.qinhan.model.ClusterStatus;
import com.qinhan.model.ForecastResult;
import com.qinhan.service.NetworkStabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkStabilityServiceImpl implements NetworkStabilityService {

    private final EwmaForecaster ewmaForecaster;


    @Override
    public void evaluateStability(ClusterStatus status) {
        String clusterName = status.getClusterName();

        // =============================================================
        // 第一步：数据准备 & EWMA 预测
        // =============================================================
        double currentLatency = status.getNetworkLatency();
        double currentLoss = status.getPacketLossRate();

        // 1. 获取完整的预测结果对象 (ForecastResult)
        // 这里面已经包含了经过 EWMA 平滑处理过的 mean 和 volatility
        ForecastResult latencyResult = ewmaForecaster.predict(clusterName, "latency", currentLatency);
        ForecastResult lossResult = ewmaForecaster.predict(clusterName, "loss", currentLoss);

        // 2. 提取预测均值 (PredMean) -> 用于计算基础风险分
        double predictedLatency = latencyResult.getPredMean();
        double predictedLoss = lossResult.getPredMean();

        // 3. 提取相对波动率 (Volatility) -> 论文核心指标！
        // 我们主要关注延迟的波动 (Jitter)，丢包的波动通常不太重要
        double relativeVolatility = latencyResult.getVolatility();


        // 将这个相对值存入 status (无量纲，例如 0.25)
        status.setVolatility(relativeVolatility);

        // =============================================================
        // 第二步：参数配置 (对应论文 Section 3.3.5)
        // =============================================================

        // A. 基准值 (Reference Values) - 用于归一化
        final double REF_LATENCY = 150.0; // 延迟基准: 150ms
        final double REF_LOSS = 2.0;      // 丢包基准: 2%

        // 🔥 修正：这里不再是 20ms，而是 "波动率基准比例"
        // 含义：我们容忍多少比例的抖动？
        // 设为 0.3 (即 30%)。如果相对波动率 > 30%，说明网络极不稳定。
        final double REF_SIGMA = 0.3;

        // B. 权重向量 (Weight Vector)
        final double W_LATENCY = 1.0;
        final double W_LOSS = 2.0;
        final double W_VOLATILITY = 0.5;  // 波动率权重

        // =============================================================
        // 第三步：计算归一化风险 (Normalized Risk)
        // =============================================================

        // 1. 延迟风险 (Linear)
        double r_latency = predictedLatency / REF_LATENCY;

        // 2. 丢包风险 (Linear)
        double r_loss = predictedLoss / REF_LOSS;

        // 3. 波动风险 (Risk of Sigma)
        // 公式: r_sigma = sigma_relative / sigma_ref
        // 例子: 如果当前波动是 15% (0.15)，基准是 30% (0.3)，那么风险就是 0.5
        double r_volatility = relativeVolatility / REF_SIGMA;

        // 增加一个保护：如果延迟极低(如10ms)，波动100%(变成20ms)其实无所谓。
        // 所以只有当 predictedLatency > 30ms 时才计入波动风险，防止低延迟下的过度敏感。
        if (predictedLatency < 30.0) {
            r_volatility = 0.0;
        }

        // 4. 异常阻断惩罚 (Circuit Breaker)
        double penalty = 0.0;
        // 熔断条件
        if (predictedLoss > 5.0 || predictedLatency > 300.0) {
            penalty = 10.0;
        }

        // 5. 总风险求和
        double totalRisk = (W_LATENCY * r_latency) +
                (W_LOSS * r_loss) +
                (W_VOLATILITY * r_volatility) +
                penalty;

        // =============================================================
        // 第四步：非线性映射 (Exponential Mapping)
        // =============================================================

        // Lambda 系数
        final double LAMBDA = 0.6;
        double score = 100.0 * Math.exp(-LAMBDA * totalRisk);
        score = Math.max(0, Math.min(100, score));

        // =============================================================
        // 第五步：保存与日志
        // =============================================================
        status.setStabilityScore(score);

        // 日志中打印相对波动率 (百分比形式，方便观察)

        String remark = String.format("总风险=%.2f (延险:%.1f, 丢险:%.1f, 波动:%.0f%%) -> 评分=%.1f",
                totalRisk, r_latency, r_loss, (relativeVolatility * 100), score);
        status.setRemark(remark);

        log.info("🧮 [决策] [{}] 原始延迟:{}ms | 预测延迟:{}ms | 波动率:{}% | {}",
                clusterName,
                String.format("%.2f", currentLatency),
                String.format("%.2f", predictedLatency),
                String.format("%.0f", relativeVolatility * 100),
                remark);
    }
}