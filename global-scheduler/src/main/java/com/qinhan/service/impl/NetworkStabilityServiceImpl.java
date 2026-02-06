package com.qinhan.service.impl;

import com.qinhan.algorithm.EwmaForecaster;
import com.qinhan.model.ClusterStatus;
import com.qinhan.model.ForecastResult;
import com.qinhan.service.NaiveStabilityComparisonService;
import com.qinhan.service.NetworkStabilityService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkStabilityServiceImpl implements NetworkStabilityService {

    private final EwmaForecaster ewmaForecaster;

    // 1. 注入刚刚写的影子服务
    private final NaiveStabilityComparisonService naiveService;

    @Value("${experiment.mode:proposed}")
    private String mode;

    @Value("${experiment.risk-alpha:0.5}")
    private double riskAlpha;

    @Override
    public void evaluateStability(ClusterStatus status) {
        String clusterName = status.getClusterName();

        // =============================================================
        // 第一步：数据准备 & EWMA 预测
        // =============================================================
        double currentLatency = status.getNetworkLatency();
//        double currentLoss = status.getPacketLossRate();

        // 1. 调用EWMA和LSTM获取完整的预测结果对象 (ForecastResult)
        // 这里面已经包含了经过 EWMA 平滑处理过的 mean 和 volatility
        ForecastResult latencyResult = ewmaForecaster.predict(clusterName, "latency", currentLatency);

        // 2. 提取实验需要的核心指标 EWMA预测均值 (PredMean)
        double predLatency = latencyResult.getPredMean();  // 预测延迟
        double riskProb = latencyResult.getRiskProbability();   // LSTM风险概率
        double volatility = latencyResult.getVolatility();      // 预测波动


        // 将这个相对值存入 status (无量纲，例如 0.25)
        status.setVolatility(volatility);

        // =============================================================
        // 🔥🔥 第二步：计算归一化成本 (Cost Model) 🔥🔥
        // 中间变量 Cost：范围 0.0 ~ 1.0，越低越好
        // =============================================================

        // 1. 定义归一化基准
        // 假设 300ms 以上延迟不仅不可接受，而且会导致 Cost=1.0 (最差)
        final double MAX_LATENCY_NORM = 300.0;

        // 2. 准备归一化数据
        double normCurrent = Math.min(1.0, currentLatency / MAX_LATENCY_NORM);
        double normPredicted = Math.min(1.0, predLatency / MAX_LATENCY_NORM);

        double cost = 0.0;
        String strategyLog = "";

        // --- 分支 A: Baseline-A (Native) ---
        if ("native".equalsIgnoreCase(mode)) {
            // Native 模式下，假设网络成本为 0 (盲目乐观)，
            // 这样算出来的分数为 100，完全由 CPU/内存 决定调度
            cost = 0.0;
            strategyLog = "Native(Blind)";
        }
        // --- 分支 B: Baseline-B (Reactive) ---
        else if ("reactive".equalsIgnoreCase(mode)) {
            // 成本 = 当前延迟
            cost = normCurrent;
            strategyLog = "Reactive(Current)";
        }
        // --- 分支 C: Variant (Prediction Only) ---
        else if ("prediction".equalsIgnoreCase(mode)) {
            // 成本 = 预测延迟 (不看风险)
            cost = normPredicted;
            strategyLog = "Prediction(NoRisk)";
        }
        // --- 分支 D: Proposed (RCGS 完整版) ---
        else {
            // 成本 = (1-α)*L + α*Psi
            // 这里的 riskProb 直接作为风险项 (0.0~1.0)

            // 【技巧】：为了防止 alpha=0.5 时，对高风险(Prob=0.9)的惩罚力度不够大
            // 我们可以稍微做一个"风险放大"，确保高风险节点的 Cost 接近 1.0
            double effectiveRisk = riskProb;
            if (riskProb > 0.6) {
                effectiveRisk = 1.0; // 熔断机制：只要风险高，直接认为风险项拉满
            }

            cost = (1 - riskAlpha) * normPredicted + (riskAlpha * effectiveRisk);

            // 确保 Cost 不超过 1.0
            cost = Math.min(1.0, cost);

            strategyLog = String.format("RCGS(α=%.1f)", riskAlpha);
        }

        // =============================================================
        // 🔥🔥 第三步：转换为 0-100 得分 (Higher is Better) 🔥🔥
        // 公式：Score = (1 - Cost) * 100
        // =============================================================

        double finalScore = (1.0 - cost) * 100.0;

        // 确保分数在 0~100 之间
        finalScore = Math.max(0.0, Math.min(100.0, finalScore));

        // =============================================================
        // 第四步：保存与日志
        // =============================================================

        // 存入 status (Go 插件读到的是这个 0-100 的数)
        status.setStabilityScore(finalScore);
        String remark = String.format("[%s] Lat:%.0f->%.0f | Risk:%.2f | Cost:%.2f -> Score:%.1f",
                strategyLog, currentLatency, predLatency, riskProb, cost, finalScore);
        status.setRemark(remark);
        // 打印 EXP-DATA
        // 注意：这里打印 finalScore，画图时记得：Proposed 曲线应该在高处(100)，掉下来代表性能变差
        log.info("EXP-DATA,{},{},{},{},{},{}",
                mode, clusterName, System.currentTimeMillis(),
                String.format("%.2f", currentLatency),
                String.format("%.2f", riskProb),
                String.format("%.2f", finalScore));


        naiveService.logComparison(status);


    }
}