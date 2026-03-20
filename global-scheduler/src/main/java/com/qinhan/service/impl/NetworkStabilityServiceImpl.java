package com.qinhan.service.impl;

import com.qinhan.algorithm.EwmaForecaster;
import com.qinhan.model.ClusterStatus;
import com.qinhan.model.ForecastResult;
import com.qinhan.service.NaiveStabilityComparisonService;
import com.qinhan.service.NetworkStabilityService;
import com.qinhan.service.RawLSTMStabilityService; // 确认你的类名是这个
import com.qinhan.util.ExperimentDataLogger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkStabilityServiceImpl implements NetworkStabilityService {

    private final EwmaForecaster ewmaForecaster;
    private final NaiveStabilityComparisonService naiveService;
    private final RawLSTMStabilityService rawLSTMStabilityService; // 注入 Raw 服务

    @Value("${experiment.risk-alpha:0.5}")
    private double riskAlpha;

    // 归一化基准
    private static final double MAX_LATENCY_NORM = 300.0;

    @Override
    public void evaluateStability(ClusterStatus status) {
        String clusterName = status.getClusterName();
        long timestamp = System.currentTimeMillis();
        double currentLatency = status.getNetworkLatency();

        // =============================================================
        // 🌟 核心数据准备 (获取 Proposed 组的预测值)
        // =============================================================
        // 调用 Port 5001
        ForecastResult ewmaResult = ewmaForecaster.predict(clusterName, "latency", currentLatency);

        double predMu = ewmaResult.getPredMean();
        double predSigma = ewmaResult.getPredDeviation();
        double volatility = ewmaResult.getVolatility();
        double proposedRiskProb = ewmaResult.getRiskProbability();

        // 统一归一化预测值 (供多个组复用)
        double normPred = Math.min(1.0, predMu / MAX_LATENCY_NORM);

        // =============================================================
        // 🧪 1. Baseline 组 (Reactive / Native)
        // =============================================================
        // 逻辑：只要没断网 (>1000ms)，就认为网络是好的 (Cost=0.1 -> Score=90)
        double costBaseline = (currentLatency > 1000) ? 1.0 : 0.1;
        double scoreBaseline = (1.0 - costBaseline) * 100.0;

        // =============================================================
        // 🧪 2. Raw-LSTM 组 (w/o AD-EWMA)
        // =============================================================
        // 逻辑：调用 5002 端口，模拟没有特征工程的情况
        double scoreRawLstm = rawLSTMStabilityService.calculateScore(clusterName, currentLatency);

        // =============================================================
        // 🧪 3. Stat-Only 组 (w/o KG-LSTM)
        // =============================================================
        // 逻辑：有 EWMA 特征，但没有 LSTM。用 Sigma (波动) 线性惩罚。
        double normSigma = Math.min(1.0, predSigma / 60.0); // 60ms 视为大波动
        double costStatOnly = (1 - riskAlpha) * normPred + (riskAlpha * normSigma);
        double scoreStatOnly = (1.0 - Math.min(1.0, costStatOnly)) * 100.0;

        // =============================================================
        // 🌟 4. Proposed 组 (Full AD-EWMA + KG-LSTM)
        // =============================================================
        // 逻辑：使用 LSTM 输出的概率作为风险项
        // 熔断保护：如果概率 > 0.8，直接拉满风险
        double effectiveRisk = (proposedRiskProb > 0.8) ? 1.0 : proposedRiskProb;

        double costProposed = (1 - riskAlpha) * normPred + (riskAlpha * effectiveRisk);
        double scoreProposed = (1.0 - Math.min(1.0, costProposed)) * 100.0;

        // =============================================================
        // 📝 最终落地 & 日志
        // =============================================================

        // 1. 设置最终分数 (给调度器用最好的 Proposed)
        status.setStabilityScore(scoreProposed);
        status.setVolatility(volatility);
        status.setRemark(String.format("Risk:%.2f -> Score:%.1f", proposedRiskProb, scoreProposed));

        // 2. 🔥 打印 4 合 1 对比日志 (核心！)
        // 格式: EXP-MULTI, Cluster, Time, Baseline, RawLSTM, StatOnly, Proposed
        log.info("EXP-MULTI,{},{},{},{},{},{},{}",
                clusterName,
                timestamp,
                String.format("%.2f", currentLatency), // 🔥 绝杀列：真实延迟
                String.format("%.2f", scoreBaseline),
                String.format("%.2f", scoreRawLstm),
                String.format("%.2f", scoreStatOnly),
                String.format("%.2f", scoreProposed)
        );

        // 2. 🔥🔥【新增】如果是 member1，直接导出到 Excel (CSV) 🔥🔥
        // 引入类: com.qinhan.util.ExperimentDataLogger
        if ("member1".equalsIgnoreCase(clusterName)) {
            ExperimentDataLogger.log(
                    clusterName,
                    timestamp,
                    currentLatency,
                    scoreBaseline,
                    scoreRawLstm,
                    scoreStatOnly,
                    scoreProposed
            );
        }

        // 3. 影子对比 (保留旧逻辑用于观察)
        naiveService.logComparison(status);
    }
}