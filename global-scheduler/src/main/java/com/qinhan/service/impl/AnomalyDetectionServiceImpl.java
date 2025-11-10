package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.AnomalyDetectionService;
import com.qinhan.util.AnomalyDetectorUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 异常检测服务实现
 * ------------------------------------------------------------
 * 当前版本仅打印检测结果，后续可扩展为：
 * - 记录异常到数据库
 * - 上报异常给全局调度器
 * - 生成伪标签供自学习模块使用
 */
@Slf4j
@Service
public class AnomalyDetectionServiceImpl implements AnomalyDetectionService {

    @Override
    public void detectClusterAnomaly(ClusterStatus status) {
        double anomalyScore = AnomalyDetectorUtil.calculateAnomalyScore(status);
        String level = AnomalyDetectorUtil.detectAnomalyLevel(status);

        // 可以扩展为 status.setAnomalyScore() 保存异常分
        status.setAnomalyScore(anomalyScore);
        status.setRemark("Anomaly Level: " + level);

        log.info("🧠 集群 [{}] 异常检测完成 -> 分数={} 状态={}",
                status.getClusterName(),
                String.format("%.2f", anomalyScore),
                level);
    }
}
