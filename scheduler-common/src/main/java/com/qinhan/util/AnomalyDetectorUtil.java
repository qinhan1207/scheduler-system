package com.qinhan.util;

import com.qinhan.model.ClusterStatus;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AnomalyDetectorUtil {
    /**
     * 计算异常分数（0~100，越高表示越异常）
     * 当前为基于规则的静态算法，可扩展为 ML 模型推理。
     */
    public static double calculateAnomalyScore(ClusterStatus status) {
        if (status == null) {
            log.warn("⚠️ AnomalyDetector: 收到空的 ClusterStatus，跳过计算。");
            return 0;
        }

        // 基础指标
        double latency = status.getNetworkLatency();       // ms
        double loss = status.getPacketLossRate();          // %
        double bandwidth = status.getNetworkBandwidth();   // Mbps
        double storage = status.getStorageUsage();         // %

        // =============== 1️⃣ 归一化处理 ===============
        // 限定合理范围，避免极端值导致计算偏移
        latency = Math.min(latency, 500);           // 超过 500ms 视为极慢
        loss = Math.min(loss, 5);                   // 丢包率上限 5%
        bandwidth = Math.max(Math.min(bandwidth, 200), 0);  // Mbps
        storage = Math.min(storage, 100);

        // =============== 2️⃣ 异常分计算 ===============
        // 延迟与丢包：直接按比例放大
        double latencyScore = (latency / 500) * 100;          // 高延迟 → 高异常分
        double lossScore = (loss / 5) * 100;                  // 高丢包 → 高异常分
        // 带宽：越小越异常
        double bandwidthScore = (1 - (bandwidth / 200)) * 100;
        // 存储：若高占用但 Pod 未明显增长，可考虑异常（此处简化处理）
        double storageScore = (storage / 100) * 100;

        // =============== 3️⃣ 加权融合 ===============
        double anomalyScore =
                0.35 * latencyScore +
                        0.30 * lossScore +
                        0.25 * bandwidthScore +
                        0.10 * storageScore;

        // 限制范围
        anomalyScore = Math.max(0, Math.min(anomalyScore, 100));

        log.debug("🤖 异常检测 => 集群={} | 延迟={}ms 丢包={} 带宽={}Mbps 存储={}%",
                status.getClusterName(), latency, loss, bandwidth, storage);

        return anomalyScore;
    }

    /**
     * 根据异常分判断是否异常
     * 阈值：70 分以上视为异常，50~70 预警，50 以下正常
     */
    public static String detectAnomalyLevel(ClusterStatus status) {
        double score = calculateAnomalyScore(status);

        String level;
        if (score >= 70) {
            level = "Critical";
        } else if (score >= 50) {
            level = "Warning";
        } else {
            level = "Normal";
        }

        log.info("⚙️ 异常检测结果 => 集群={} | 异常分={}% | 状态={}",
                status.getClusterName(), String.format("%.2f", score), level);

        return level;
    }
}
