package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.EwmaFeatureVector;
import com.qinhan.model.PredictionResult;
import com.qinhan.service.NetworkStabilityService;
import com.qinhan.util.LSTMPredictor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class NetworkStabilityServiceImpl implements NetworkStabilityService {

    @Value("${experiment.risk-alpha:0.5}")
    private double riskAlpha;

    // 归一化基准
    private static final double MAX_LATENCY_NORM = 300.0;

    @Override
    public void evaluateStability(ClusterStatus status) {
        String clusterName = status.getClusterName();

        // 由 LSA 侧已经计算好的 EWMA 特征值
        double predMu = status.getLatencyMeanFeature();
        double predSigma = status.getLatencyDeviationFeature();
        double vt = status.getVolatility();

        log.info("GS接收特征摘要: cluster={} mu={}ms sigma={}ms vt={}% agg_latency={} peerRawStats={}",
                clusterName,
                String.format("%.2f", predMu),
                String.format("%.2f", predSigma),
                String.format("%.1f", vt * 100),
                status.getNetworkLatency(),
                status.getPeerRawStats()
        );

        log.debug("GS接收完整ClusterStatus: {}", status);
        log.debug("GS接收peerRawStats: {}", status.getPeerRawStats());
        log.debug("GS接收ftWindow: {}", status.getFtWindow());

        // 统一归一化预测值 (供多个组复用)
        double normPred = Math.min(1.0, predMu / MAX_LATENCY_NORM);

        // 调用 LSTM 获取故障风险概率：主路径使用窗口推理，单点接口仅兜底。
        List<EwmaFeatureVector> ftWindow = status.getFtWindow();
        PredictionResult lstmResult;
        String inferenceMode;
        if (ftWindow != null && !ftWindow.isEmpty()) {
            inferenceMode = "window";
            lstmResult = LSTMPredictor.predictByWindow(clusterName, ftWindow);
            if (!lstmResult.isSuccess()) {
                log.warn("窗口推理失败，回退单点推理: cluster={}, msg={}", clusterName, lstmResult.getMessage());
                inferenceMode = "single-fallback";
                lstmResult = LSTMPredictor.predict(clusterName, predMu, predSigma, vt);
            }
        } else {
            log.warn("ftWindow 为空，回退单点推理: cluster={}", clusterName);
            inferenceMode = "single-no-window";
            lstmResult = LSTMPredictor.predict(clusterName, predMu, predSigma, vt);
        }

        int windowSize = ftWindow == null ? 0 : ftWindow.size();
        log.info("Python推理 prob: cluster={} prob={} fault={} mode={} windowSize={}",
                clusterName,
                String.format("%.4f", lstmResult.getProbability()),
                lstmResult.isFault(),
                inferenceMode,
                windowSize);

        double proposedRiskProb = lstmResult.getProbability();

        // 熔断保护
        double effectiveRisk = (proposedRiskProb > 0.8) ? 1.0 : proposedRiskProb;
        double costProposed = (1 - riskAlpha) * normPred + (riskAlpha * effectiveRisk);
        double scoreProposed = (1.0 - Math.min(1.0, costProposed)) * 100.0;

        // 设置最终分数
        status.setStabilityScore(scoreProposed);
        status.setVolatility(vt);
        status.setRemark(String.format("Risk:%.2f -> Score:%.1f", proposedRiskProb, scoreProposed));
    }
}