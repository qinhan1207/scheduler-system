package com.qinhan.decision;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.SchedulingEvent;
import com.qinhan.service.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * 🌐 GlobalDecisionEngine
 * 全局调度决策引擎：
 * 1️⃣ 接收来自 Bridge 的 ResourceBinding 事件
 * 2️⃣ 读取各集群健康信息
 * 3️⃣ 选择最优目标集群
 * 4️⃣ 对比 Karmada 原生调度结果并输出建议
 */
@Slf4j
@Component
public class GlobalDecisionEngine {

    private final ClusterService clusterService;

    public GlobalDecisionEngine(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    /**
     * 核心方法：评估一次 ResourceBinding 事件
     */
    public void evaluateSchedulingEvent(SchedulingEvent event) {
        try {
            List<ClusterStatus> allClusters = clusterService.getAllClusterStatus();

            if (allClusters.isEmpty()) {
                log.warn("⚠️ 当前无集群状态数据，跳过调度评估。");
                return;
            }

            // 1️⃣ 获取原始目标集群
            List<String> karmadaTargets = event.getTargetClusters();

            // 2️⃣ 选出健康分最高的集群
            Optional<ClusterStatus> best = allClusters.stream()
                    .max(Comparator.comparingDouble(ClusterStatus::getHealthScore));

            if (best.isEmpty()) {
                log.warn("⚠️ 无法评估调度，未找到健康分最高的集群。");
                return;
            }

            ClusterStatus bestCluster = best.get();
            String bestName = bestCluster.getClusterName();
            double bestScore = bestCluster.getHealthScore();

            // 3️⃣ 对比原生调度与GS推荐
            boolean sameAsKarmada = karmadaTargets != null && karmadaTargets.contains(bestName);

            log.info("""
                            🧠 全局调度评估报告：
                            • Workload：{}/{}
                            • 原生目标：{}
                            • 推荐目标：{}（健康分 {}）
                            • 评估结论：{}
                            """,
                    event.getNamespace(),
                    event.getWorkloadName(),
                    karmadaTargets,
                    bestName,
                    String.format("%.2f", bestScore),
                    sameAsKarmada ? "✅ 一致，无需调整" : "⚠️ 建议重新调度到更健康集群");

        } catch (Exception e) {
            log.error("❌ 调度评估失败: {}", e.getMessage());
        }
    }
}
