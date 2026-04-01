package com.qinhan.service.impl;

import com.qinhan.model.ClusterScore;
import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ClusterScoreService;
import com.qinhan.service.MemberClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * ClusterScoreServiceImpl
 * 核心评分引擎：融合了以下三层逻辑
 * 1. 静态健康度 (Health): 资源是否充足
 * 2. 动态稳定性 (Stability): EWMA 预测网络是否即将拥塞
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ClusterScoreServiceImpl implements ClusterScoreService {

    private final MemberClusterService memberClusterService;

    @Override
    public ClusterScore calculateScore(String clusterName, String targetCluster) {

        String realName = clusterName;
        if (clusterName.startsWith("cluster")) {
            realName = clusterName.replace("cluster", "member");
            // 打印一条调试日志，证明补丁生效了
            log.debug("🔄 [NameMapping] 将请求 [{}] 映射为本地名称 [{}] 进行查询", clusterName, realName);
        }

        // 为了在 Lambda 表达式中使用，需要确保变量是 effective final
        String searchName = realName;

        // 1.获取集群状态
        List<ClusterStatus> allStatus = memberClusterService.getAllClusterStatus();

        ClusterStatus status = allStatus.stream()
                .filter(s -> searchName.equals(s.getClusterName()))
                .findFirst()
                .orElse(null);

        if (status == null) {
            // 注意：这里打印日志还是用原始的 clusterName，方便和 Karmada 日志对应
            log.warn("⚠️ 未找到集群 [{}] (映射为: {}) 的状态记录，无法计算评分。", clusterName, searchName);
            return ClusterScore.builder()
                    .clusterName(clusterName) // 返回给插件时，必须用它认识的原始名字
                    .healthScore(0)
                    .finalScore(0)
                    .reason("未找到集群状态数据")
                    .build();
        }


        // 我们已经在 NetworkStabilityServiceImpl 里运用了复杂的公式 (LSTM + Alpha)
        // 计算出了最终得分 (0-100)。这里绝对不要再做任何"截断"或"修改"。
        // 让 Go 插件看到的，就是算法算出来的原值。
        double finalScore = status.getStabilityScore();

        // 2. 构造 Reason 字符串 (仅用于显示，不影响调度)
        String reason = status.getRemark(); // 直接复用上一层生成的详细备注
        if (reason == null || reason.isEmpty()) {
            reason = String.format("Score:%.2f", finalScore);
        }


        // 3. 构造结果
        ClusterScore result = ClusterScore.builder()
                .clusterName(clusterName)
                .healthScore(finalScore)
             .finalScore(finalScore)
                .reason(reason)
                .build();

        // 4. 打印最终决策日志 (这是您论文数据的最终出口)
        // 格式: [FINAL-DECISION] 集群 -> 分数
        log.info("🏁 [最终输出] To-Go-Plugin: Cluster=[{}] | FinalScore={} | Reason=[{}]",
                clusterName,
                String.format("%.2f", finalScore),
                reason);

        return result;
    }
}
