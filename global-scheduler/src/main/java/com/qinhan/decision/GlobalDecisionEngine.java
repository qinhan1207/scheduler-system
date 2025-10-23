package com.qinhan.decision;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.ScheduleDecision;
import com.qinhan.model.SchedulingEvent;
import com.qinhan.service.BindingUpdaterService;
import com.qinhan.service.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
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
    public ScheduleDecision evaluateSchedulingEvent(SchedulingEvent event) {

        ScheduleDecision.ScheduleDecisionBuilder builder = ScheduleDecision.builder()
                .workloadName(event.getWorkloadName())
                .namespace(event.getNamespace())
                .needReschedule(false)
                .reason("初始状态");

        try {
            List<ClusterStatus> allClusters = clusterService.getAllClusterStatus();

            if (allClusters.isEmpty()) {
                builder.reason("⚠\uFE0F 当前无集群状态数据");
                log.warn("⚠️ 当前无集群状态数据，跳过调度评估。");
                return builder.build();
            }

            // 1️⃣ 选出健康分最高的集群
            Optional<ClusterStatus> bestOpt = allClusters.stream()
                    .max(Comparator.comparingDouble(ClusterStatus::getHealthScore));

            if (bestOpt.isEmpty()) {
                builder.reason("⚠\uFE0F 未找到健康分最高的集群");
                log.warn("⚠️ 无法评估调度，未找到健康分最高的集群。");
                return builder.build();
            }

            ClusterStatus bestCluster = bestOpt.get();
            String bestName = bestCluster.getClusterName();
            double bestScore = bestCluster.getHealthScore();

            // 2️⃣ 判断是否与 Karmada 原生调度一致
            List<String> karmadaTargets = event.getTargetClusters();
            boolean sameAsKarmada = karmadaTargets != null && karmadaTargets.contains(bestName);

            // 3️⃣ 设置决策结果
            builder.recommendedCluster(bestName)
                    .recommendedScore(bestScore)
                    .needReschedule(!sameAsKarmada)
                    .reason(sameAsKarmada ? "✅ 一致，无需调整" : "⚠️ 建议重新调度到更健康集群");
            // 4️⃣ 打印评估报告
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
        return builder.build();
    }
}
