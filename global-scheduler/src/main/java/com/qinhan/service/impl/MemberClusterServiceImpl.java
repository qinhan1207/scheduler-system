package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.MemberClusterService;
import com.qinhan.util.HealthEvaluator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class MemberClusterServiceImpl implements MemberClusterService {

    private final ConcurrentHashMap<String, ClusterStatus> clusterMap = new ConcurrentHashMap<>();

    /**
     * 更新集群状态
     */
    @Override
    public void updateClusterStatus(ClusterStatus status) {
        // 计算集群健康状态
        String healthStatus = HealthEvaluator.evaluate(status);
        status.setHealthStatus(healthStatus);

        // 计算健康分值
        double healthScore = HealthEvaluator.calculateScore(status);
        status.setHealthScore(healthScore);

        clusterMap.put(status.getClusterName(), status);

        log.info("🩺 更新集群 [{}] 状态 => 健康等级={} | 健康分数={}",
                status.getClusterName(),
                healthStatus,
                String.format("%.2f", healthScore));
    }

    /**
     * 查看所有集群
     * @return 所有集群信息
     */
    @Override
    public List<ClusterStatus> getAllClusterStatus() {
        return new ArrayList<>(clusterMap.values());
    }

    /**
     * 定期对集群进行评分和健康的修改
     */
    @Scheduled(fixedRateString = "${global.cluster.health-eval-interval:10000}")
    public void periodicEvaluateAll() {
        log.info("🩺 定期健康评估任务开始...");

        clusterMap.forEach((name, status) -> {
            try {
                // ✅ 计算健康状态和健康分
                String healthStatus = HealthEvaluator.evaluate(status);
                double healthScore = HealthEvaluator.calculateScore(status);

                // ✅ 只更新字段，不重复 put
                status.setHealthStatus(healthStatus);
                status.setHealthScore(healthScore);

                log.debug("🩸 集群 [{}] 健康评估完成 -> 状态={} | 分数={}",
                        name, healthStatus, String.format("%.2f", healthScore));

            } catch (Exception e) {
                log.warn("⚠️ 集群 [{}] 健康评估失败: {}", name, e.getMessage());
            }
        });

        log.info("✅ 本轮健康评估完成，共评估 {} 个集群", clusterMap.size());
    }

}