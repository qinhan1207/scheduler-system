package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.RawNetworkStats;
import com.qinhan.service.NetworkStabilityService;
import com.qinhan.service.MemberClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberClusterServiceImpl implements MemberClusterService {

    private final ConcurrentHashMap<String, ClusterStatus> clusterMap = new ConcurrentHashMap<>();

    private static final double LATENCY_SLA_THRESHOLD = 300.0;
    private static final double MAX_PENALTY_LATENCY = 2000.0;

    // 注入异常检测服务 (包含 EWMA 预测)
    private final NetworkStabilityService networkStabilityService;

    @Override
    public void updateClusterStatus(ClusterStatus status) {
        // 1. 聚合网络探测结果：LSA 只上报原始探测值，这里做延迟/丢包的统一口径计算
        status = aggregateNetworkMetrics(status);

        // 打印聚合后的 ClusterStatus 详细信息
        log.info("📊 聚合后集群状态 [{}]: 网络延迟={}ms, 丢包率={}%, 对等节点延迟={}",
                status.getClusterName(),
                String.format("%.2f", status.getNetworkLatency()),
                String.format("%.2f", status.getPacketLossRate()),
                status.getPeerLatencyMap());


        // 2. 🔥 核心优化：调用稳定性服务进行评估
        // 语义非常通顺： "请帮我评估一下这个集群的稳定性"
        networkStabilityService.evaluateStability(status);


        // 4. 更新内存缓存
        clusterMap.put(status.getClusterName(), status);

        log.debug("✅ 集群 [{}] 更新完毕: Anomaly={}, Stability={}",
                status.getClusterName(),
                String.format("%.0f", status.getAnomalyScore()),
                String.format("%.0f", status.getStabilityScore()));
    }

    @Override
    public List<ClusterStatus> getAllClusterStatus() {
        return new ArrayList<>(clusterMap.values());
    }


    // 聚合网络探测结果
//    private ClusterStatus aggregateNetworkMetrics(ClusterStatus status) {
//        Map<String, RawNetworkStats> rawMap = status.getPeerRawStats();
//
//        // 如果没有邻居数据，直接判为最大延迟
//        if (rawMap == null || rawMap.isEmpty()) {
//            status.setNetworkLatency(MAX_PENALTY_LATENCY);
//            status.setPacketLossRate(100.0);
//            status.setPeerLatencyMap(new HashMap<>());
//            return status;
//        }
//
//        Map<String, Double> peerLatencyMap = new HashMap<>();
//        // 有效延迟总和
//        double validLatencySum = 0.0;
//        // 有效链路数
//        int validLinkCount = 0;
//        // 原始丢包率总和
//        double totalRawLossRate = 0.0;
//        // 总链路数
//        int countedLinks = 0;
//
//        for (Map.Entry<String, RawNetworkStats> entry : rawMap.entrySet()) {
//            RawNetworkStats stats = entry.getValue();
//            if (stats == null) {
//                continue;
//            }
//
//            double avgLatency = stats.getAvgLatency();
//            double lossRate = stats.getLossRate();
//            // 核心规则：SLA 过滤器 (Heuristic Filter Rule)
//            // 论文对应：定义健康链路集合 H = {link | loss < 10% AND latency < 500ms}
//            // 目的：剔除长尾节点(Stragglers)对平均延迟的拉低影响
////            boolean isLinkHealthy = (lossRate < 0.1) && (avgLatency < LATENCY_SLA_THRESHOLD);
////
////            if (isLinkHealthy) {
////                validLatencySum += avgLatency;
////                validLinkCount++;
////            }
//
//
//            if (avgLatency > 0) {
//                validLatencySum += avgLatency;
//                validLinkCount++;
//            }
//
//            totalRawLossRate += lossRate;
//            countedLinks++;
//
//            peerLatencyMap.put(entry.getKey(), avgLatency);
//
//        }
//
//        // 1. 计算平均延迟 (Latency Aggregation)
//        // 逻辑：如果至少有一条健康链路，取健康链路的平均值；否则给最大惩罚。
//        // 公式：L_agg = (N_valid > 0) ? (ΣL_valid / N_valid) : L_max_penalty
//        double networkLatency = validLinkCount > 0
//                ? validLatencySum / validLinkCount
//                : MAX_PENALTY_LATENCY;
//
//        // 2. 计算平均丢包率 (Loss Aggregation)
//        // 逻辑：如果能连通健康节点，视为丢包率为0(为了亲和性/稳定性计算不被双重惩罚)；
//        //      或者：保留全局丢包率。
//        // *SCI 实验版策略*：
//        // 这里我们稍微修改一下逻辑：如果 validLinkCount > 0，说明只要有路能走，我就认为网络是通的(0% loss)；
//        // 只有当所有路都烂了，我才算具体的丢包率。这是一种"乐观聚合"。
//
////        double packetLossRate = validLinkCount > 0
////                ? 0.0
////                : (countedLinks > 0 ? totalRawLossRate / countedLinks : 100.0);
//
//        double packetLossRate = (countedLinks > 0)
//                ? totalRawLossRate / countedLinks
//                : 100.0;
//
//        status.setNetworkLatency(networkLatency);
//        status.setPacketLossRate(packetLossRate);
//        status.setPeerLatencyMap(peerLatencyMap);
//
//        return status;
//    }

// 聚合网络探测结果 (已修正为 Min 策略)
private ClusterStatus aggregateNetworkMetrics(ClusterStatus status) {
    Map<String, RawNetworkStats> rawMap = status.getPeerRawStats();

    // 0. 边界情况处理：如果没有邻居数据，直接判为最大延迟（孤岛）
    if (rawMap == null || rawMap.isEmpty()) {
        status.setNetworkLatency(MAX_PENALTY_LATENCY);
        status.setPacketLossRate(100.0);
        status.setPeerLatencyMap(new HashMap<>());
        return status;
    }

    Map<String, Double> peerLatencyMap = new HashMap<>();

    // 初始化统计变量
    double minLatency = Double.MAX_VALUE; // 🔥 关键：找最小值
    boolean foundValidPath = false;       // 是否存在至少一条通畅的路径

    double totalRawLossRate = 0.0;
    int countedLinks = 0;

    for (Map.Entry<String, RawNetworkStats> entry : rawMap.entrySet()) {
        RawNetworkStats stats = entry.getValue();
        if (stats == null) continue;

        double avgLatency = stats.getAvgLatency();
        double lossRate = stats.getLossRate();

        // === 🔥 核心修改开始 🔥 ===
        // 只要有一条链路是通的 (Latency > 0)，我们就记录它
        if (avgLatency > 0) {
            // 我们取所有邻居中延迟"最低"的那个，代表本节点的真实网络能力
            // 逻辑：Member2 -> Member3 (20ms), Member2 -> Member1 (120ms)
            //      ==> Member2 的能力是 20ms (它是健康的)
            if (avgLatency < minLatency) {
                minLatency = avgLatency;
            }
            foundValidPath = true;
        }
        // === 核心修改结束 ===

        totalRawLossRate += lossRate;
        countedLinks++;

        peerLatencyMap.put(entry.getKey(), avgLatency);
    }

    // 1. 设置聚合延迟 (Latency)
    // 如果找到了有效路径，就用 minLatency；否则用惩罚值
    double finalLatency = foundValidPath ? minLatency : MAX_PENALTY_LATENCY;

    // 2. 设置聚合丢包率 (Packet Loss)
    // 同样逻辑：如果只是连 Member1 丢包，但连 Member3 不丢包，那说明我没问题
    // 这里为了简单，我们保持平均值，但在网络良好的实验中通常 loss 为 0
    double finalLossRate = (countedLinks > 0)
            ? totalRawLossRate / countedLinks
            : 100.0;

    // (可选优化) 如果您希望丢包率也"择优"，可以用下面这行代替上面：
    // double finalLossRate = foundValidPath ? 0.0 : 100.0;

    status.setNetworkLatency(finalLatency);
    status.setPacketLossRate(finalLossRate);
    status.setPeerLatencyMap(peerLatencyMap);

    // 打印调试日志，方便您确认是否生效
    if (foundValidPath) {
        log.debug("🔍 [聚合策略] 集群={} | 原始={} | 选取Min={}",
                status.getClusterName(), peerLatencyMap, finalLatency);
    }

    return status;
}


}