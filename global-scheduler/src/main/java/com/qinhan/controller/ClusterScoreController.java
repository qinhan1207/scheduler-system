package com.qinhan.controller;

import com.qinhan.model.ClusterScore;
import com.qinhan.service.ClusterScoreService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

/**
 * ClusterScoreController
 * 提供给 Karmada 调度插件访问的 HTTP 接口。
 */
@Slf4j
@RestController
@RequestMapping("/api/advisor")
@RequiredArgsConstructor
public class ClusterScoreController {

    private final ClusterScoreService clusterScoreService;

    /**
     * 获取指定集群的健康评分
     * 示例请求：
     *   GET /api/advisor/score?cluster=kwok-cluster01
     *
     * @param clusterName 集群名称
     * @return ClusterScore 健康分结果
     */
    @GetMapping("/score")
    public ClusterScore getClusterScore(@RequestParam("cluster") String clusterName) {
        log.info("📩 收到插件评分请求: cluster={}", clusterName);
        ClusterScore score = clusterScoreService.calculateScore(clusterName);
        log.info("📤 返回评分结果: {}", score);
        return score;
    }

    /**
     * 测试接口（可选）
     * 用于验证 Java 服务运行正常。
     * 示例请求：
     *   GET /api/advisor/ping
     */
    @GetMapping("/ping")
    public String ping() {
        return "✅ Global Scheduler is running!";
    }
}
