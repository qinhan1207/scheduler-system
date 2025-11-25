package com.qinhan.service.impl;

import com.qinhan.model.ClusterScore;
import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ClusterScoreService;
import com.qinhan.service.MemberClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * ClusterScoreServiceImpl
 * 核心评分引擎：融合了以下三层逻辑
 * 1. 静态健康度 (Health): 资源是否充足
 * 2. 动态稳定性 (Stability): EWMA 预测网络是否即将拥塞
 * 3. 亲和性修正 (Affinity): 与目标微服务的物理距离
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterScoreServiceImpl implements ClusterScoreService {

    private final MemberClusterService memberClusterService;

    @Override
    public ClusterScore calculateScore(String clusterName, String targetCluster) {

        // 1.获取集群状态
        List<ClusterStatus> allStatus = memberClusterService.getAllClusterStatus();

        ClusterStatus status = allStatus.stream()
                .filter(s -> clusterName.equals(s.getClusterName()))
                .findFirst()
                .orElse(null);

        if (status == null) {
            log.warn("⚠️ 未找到集群 [{}] 的状态记录，无法计算评分。", clusterName);
            return ClusterScore.builder()
                    .clusterName(clusterName)
                    .healthScore(0)
                    .reason("未找到集群状态数据")
                    .build();
        }


        // ============================================================
        // 第一步：计算基础分 (Base Score)
        // 策略：预测稳定性占主导 (70%)，静态资源占辅助 (30%)
        // ============================================================
        // 🔥 核心修改：融合 静态健康分(Health) 与 动态预测分(Stability)
        double health = status.getHealthScore();       // 资源+当前网络 (0-100)
        double stability = status.getStabilityScore(); // EWMA预测未来 (0-100)

        // 决策逻辑：
        // 1. 稳定性(预测)占主导：如果预测要崩，分数必须拉低
        // 2. 健康度(现状)作辅助：如果预测没问题，再看资源够不够

        // 权重配置：预测稳定性 70% + 静态健康度 30%
        // 这样一旦 Stability 掉到 60 (熔断线)，总分很难超过 70，会被 Karmada 过滤掉
        double baseScore = (stability * 0.7) + (health * 0.3);

        // ============================================================
        // 第二步：亲和性协同修正 (Affinity Correction) - 论文核心公式
        // 如果指定了 targetCluster，计算物理距离并给予加分
        // ============================================================

        double affinityBonus = 0.0;
        String affinityReason = "";

        if (targetCluster != null && !targetCluster.isEmpty()) {
            // 情况 A: 目标就是自己 -> 同集群亲和，网络耗时为0，加满分
            if (clusterName.equals(targetCluster)) {
                affinityBonus = 20.0;
                affinityReason = " (同集群)";
            }
            // 情况 B: 目标是其他集群 -> 查阅全域网络矩阵
            else {
                Map<String, Double> latencyMap = status.getPeerLatencyMap();
                if (latencyMap != null && latencyMap.containsKey(targetCluster)) {
                    double latency = latencyMap.get(targetCluster);

                    // 📝 论文公式实现：指数衰减效用函数 (Exponential Decay)
                    // 公式：Bonus = 20 * e^(-latency / K)
                    // 含义：延迟越低加分越多；当延迟超过 30ms 后，加分迅速衰减为 0
                    if (latency < 900) { // 900ms 以上视为不通
                        affinityBonus = 20.0 * Math.exp(-latency / 30.0);
                        affinityReason = String.format(" (延迟%.1fms)", latency);
                    }
                }
            }
        }

        // ============================================================
        // 第三步：融合与熔断 (Fusion & Circuit Breaking)
        // ============================================================
        double finalScore = baseScore + affinityBonus;

        // 🔒 熔断逻辑：如果预测稳定性太差 (Stability < 60)，
        // 无论亲和性多好，强制限制最高分，防止调度到即将故障的节点
        if (stability < 60) {
            finalScore = Math.min(finalScore, 55.0); // 强制不及格
            affinityReason += " [预测熔断]";
        }

        // 封顶 100 分
        finalScore = Math.max(0, Math.min(100, finalScore));

        // 构造原因描述 (方便 Karmada 日志查看)
        // 构造 Reason 字符串供 Dashboard 显示
        String reason;
        if (stability < 60) {
            reason = String.format("预测风险高(Stability=%.0f)%s", stability, affinityReason);
        } else {
            reason = String.format("基础:%.0f%s", baseScore, affinityBonus > 0.5 ? affinityReason : "");
        }

        ClusterScore result = ClusterScore.builder()
                .clusterName(clusterName)
                .healthScore(finalScore)
                .reason(reason)
                .build();

        // 打印详细日志用于论文实验分析
        log.info("📊 评分 [{}] -> Target:{} | Base:{}(S:{}/H:{}) | Affinity:+{} => Final:{}",
                clusterName,
                (targetCluster == null ? "无" : targetCluster),
                String.format("%.1f", baseScore),
                String.format("%.0f", stability),
                String.format("%.0f", health),
                String.format("%.1f", affinityBonus),
                String.format("%.1f", finalScore));

        return result;
    }
}
