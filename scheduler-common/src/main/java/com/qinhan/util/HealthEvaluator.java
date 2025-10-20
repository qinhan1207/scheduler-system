package com.qinhan.util;

import com.qinhan.model.ClusterStatus;
import lombok.extern.slf4j.Slf4j;

/**
 * 集群健康评估工具类
 * --------------------------------------------------
 * 用于根据 CPU / 内存等指标评估集群整体健康状态。
 * 当前版本使用简单的阈值逻辑，可在未来扩展为：
 * - 加权评分模型（CPU/内存/节点状态/Pod失败率等）
 * - 动态阈值（基于历史均值）
 * - 支持配置文件加载阈值
 */
@Slf4j
public class HealthEvaluator {

    // 阈值（可根据实验情况调整）
    private static final double CPU_WARNING = 75.0;
    private static final double CPU_CRITICAL = 90.0;

    private static final double MEM_WARNING = 75.0;
    private static final double MEM_CRITICAL = 90.0;

    /**
     * 根据 CPU 与内存使用率计算健康等级
     *
     * @param status 集群状态对象
     * @return 返回 "Healthy" / "Warning" / "Critical"
     */
    public static String evaluate(ClusterStatus status) {
        if (status == null) {
            log.warn("⚠️ HealthEvaluator: 收到空的 ClusterStatus，跳过计算。");
            return "Unknown";
        }

        double cpu = status.getCpuUsage();
        double mem = status.getMemoryUsage();

        // 健康度计算逻辑
        String health;
        if (cpu < CPU_WARNING && mem < MEM_WARNING) {
            health = "Healthy";
        } else if (cpu < CPU_CRITICAL && mem < MEM_CRITICAL) {
            health = "Warning";
        } else {
            health = "Critical";
        }

        // 打印详细日志
        log.debug("🩺 健康评估 => 集群={} | CPU={}% | MEM={}% | 状态={}",
                status.getClusterName(),
                String.format("%.2f", cpu),
                String.format("%.2f", mem),
                health);

        return health;
    }

    /**
     * 可选：返回一个数值评分（0~100）
     * 用于量化健康度，方便后续排序或聚合分析。
     */
    public static double calculateScore(ClusterStatus status) {
        double cpu = status.getCpuUsage();
        double mem = status.getMemoryUsage();

        // 简单加权平均：CPU、内存各占一半
        double score = 100 - ((cpu + mem) / 2);
        return Math.max(0, Math.min(score, 100)); // 限制在 [0,100]
    }
}
