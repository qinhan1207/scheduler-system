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
        if (status == null) return 0;

        //  网络维度
        double latency = status.getNetworkLatency();
        double loss = status.getPacketLossRate();

        // 归一化：延迟 > 300ms 算满分异常，丢包 > 5% 算满分异常
        // 1.丢包维度
        // ⚠️ 注意：由于 LSA 采用 ping -c 5，单次探测最小非零丢包率为 20%。
        // 因此，这里的 "5" 阈值实际上起到了 "Zero Tolerance" (零容忍) 的作用。
        // 只要发现任何丢包 (loss >= 20.0)，丢包分直接拉满 (50分)。
        // 2. 延迟维度
        // ⚠️ 注意：300ms 是微服务调用的 "硬" 不可用线。
        // 超过 300ms 会导致 TCP 吞吐量剧降及上层业务超时。
        double anomalyScore = (Math.min(latency, 300) / 300.0 * 50) +
                (Math.min(loss, 5) / 5.0 * 50);


        log.debug("🤖 异常检测 => 集群={} | 延迟={}ms 丢包={}",
                status.getClusterName(), latency, loss);

        return Math.max(0, Math.min(100, anomalyScore));
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
