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

    // 定义预测阈值，超过此值认为未来有风险
    private static final double PREDICTED_LATENCY_THRESHOLD = 200.0; // ms

    @Override
    public void detectClusterAnomaly(ClusterStatus status) {
        String clusterName = status.getClusterName();

        // 1. 基础的静态检测 (保留你原有的逻辑)
        double staticAnomalyScore = AnomalyDetectorUtil.calculateAnomalyScore(status);

        // 2. 🔥 EWMA 预测逻辑 (新增)
        // 预测下一时刻的网络延迟
        double predictedLatency = ewmaForecaster.predict(clusterName, "latency", status.getNetworkLatency());
        // 预测下一时刻的丢包率
        double predictedLoss = ewmaForecaster.predict(clusterName, "loss", status.getPacketLossRate());

        // 3. 计算“稳定性得分” (Stability Score)
        // 如果预测值很糟糕，哪怕当前值还行，也要扣分 —— 这就是“预测性调度”
        double stabilityScore = 100.0;

        if (predictedLatency > PREDICTED_LATENCY_THRESHOLD) {
            stabilityScore -= 40; // 预测未来高延迟，重罚
            log.warn("⚠️ [主动熔断预警] 集群 {} 预测延迟将达到 {}ms，存在抖动风险！",
                    clusterName, String.format("%.2f", predictedLatency));
        }

        if (predictedLoss > 2.0) { // 预测丢包 > 2%
            stabilityScore -= 30;
        }

        // 4. 将预测结果回写到 Status 中，供调度器使用
        status.setStabilityScore(Math.max(0, stabilityScore));
        status.setAnomalyScore(staticAnomalyScore); // 保留静态分作为参考

        // 更新备注，方便调试查看
        String remark = String.format("PredLatency:%.0fms, PredLoss:%.1f%%", predictedLatency, predictedLoss);
        status.setRemark(remark);

        log.info("🧠 综合检测 [{}] -> 静态异常分:{} | 预测稳定性:{} | {}",
                clusterName,
                String.format("%.2f", staticAnomalyScore),
                String.format("%.2f", stabilityScore),
                remark);
    }
}