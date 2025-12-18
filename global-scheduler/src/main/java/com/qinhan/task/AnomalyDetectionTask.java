//package com.qinhan.task;
//
//import com.qinhan.model.ClusterStatus;
//import com.qinhan.service.AnomalyDetectionService;
//import com.qinhan.service.MemberClusterService;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
///**
// * 异常检测定时任务
// * ------------------------------------------------------------
// * 每隔固定时间执行一次异常检测，用于识别潜在异常集群。
// *
// * 配置示例（application.yml）：
// *  global:
// *    cluster:
// *      anomaly-detect-interval: 60000  # 单位 ms
// */
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class AnomalyDetectionTask {
//
//    private final MemberClusterService memberClusterService;
//    private final AnomalyDetectionService anomalyDetectionService;
//
//    /**
//     * 定期执行异常检测任务
//     * fixedRateString: 两次任务开始的时间间隔
//     * 默认每 60 秒执行一次，可在配置文件中覆盖
//     */
//    @Scheduled(fixedRateString = "${global.cluster.anomaly-detect-interval:60000}")
//    public void runAnomalyDetection() {
//        log.info("🧠 [AnomalyDetectionTask] 开始执行异常检测任务...");
//
//        try {
//            List<ClusterStatus> clusters = memberClusterService.getAllClusterStatus();
//            if (clusters.isEmpty()) {
//                log.info("📭 当前无集群状态数据，跳过检测。");
//                return;
//            }
//
//            clusters.forEach(status -> {
//                try {
//                    anomalyDetectionService.detectClusterAnomaly(status);
//                } catch (Exception e) {
//                    log.warn("⚠️ 集群 [{}] 异常检测失败: {}", status.getClusterName(), e.getMessage());
//                }
//            });
//
//            log.info("✅ 异常检测任务完成，共检测 {} 个集群。", clusters.size());
//
//        } catch (Exception e) {
//            log.error("❌ 异常检测任务执行出错: {}", e.getMessage(), e);
//        }
//    }
//}
