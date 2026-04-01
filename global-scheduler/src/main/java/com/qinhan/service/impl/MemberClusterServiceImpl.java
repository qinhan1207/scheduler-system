package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.NetworkStabilityService;
import com.qinhan.service.NetworkAggregationService;
import com.qinhan.service.MemberClusterService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class MemberClusterServiceImpl implements MemberClusterService {

    private final ConcurrentHashMap<String, ClusterStatus> clusterMap = new ConcurrentHashMap<>();

    // 注入稳定性评估服务（GS 侧 LSTM）
    private final NetworkStabilityService networkStabilityService;
    private final NetworkAggregationService networkAggregationService;

    @Override
    public void updateClusterStatus(ClusterStatus status) {
        // 1. 优先使用边缘侧已计算的聚合值；仅在缺失时回退到云端聚合（兼容旧版本上报）
        if (needCloudAggregation(status)) {
            status = networkAggregationService.aggregate(status);
            log.debug("回退云端聚合集群状态 [{}]: 网络延迟={}ms, 丢包率={}%, 对等节点延迟={}",
                    status.getClusterName(),
                    String.format("%.2f", status.getNetworkLatency()),
                    String.format("%.2f", status.getPacketLossRate()),
                    status.getPeerLatencyMap());
        } else {
            log.debug("使用边缘聚合集群状态 [{}]: 网络延迟={}ms, 丢包率={}%, 对等节点数={}",
                    status.getClusterName(),
                    String.format("%.2f", status.getNetworkLatency()),
                    String.format("%.2f", status.getPacketLossRate()),
                    status.getPeerRawStats() == null ? 0 : status.getPeerRawStats().size());
        }

        // 2. 调用稳定性服务进行评估
        networkStabilityService.evaluateStability(status);

        // 3. 更新内存缓存
        clusterMap.put(status.getClusterName(), status);

        log.debug("✅ 集群 [{}] 更新完毕: Stability={}",
            status.getClusterName(),
            String.format("%.0f", status.getStabilityScore()));
    }

    @Override
    public List<ClusterStatus> getAllClusterStatus() {
        return new ArrayList<>(clusterMap.values());
    }

    private boolean needCloudAggregation(ClusterStatus status) {
        // 仅当边缘未提供可用聚合值时，才在云端回退聚合。
        return status.getNetworkLatency() <= 0 || status.getPacketLossRate() < 0;
    }
}