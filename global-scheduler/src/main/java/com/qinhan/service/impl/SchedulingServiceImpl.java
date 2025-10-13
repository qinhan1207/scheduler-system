package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.model.SchedulingRequest;
import com.qinhan.model.SchedulingResponse;
import com.qinhan.service.ClusterService;
import com.qinhan.service.SchedulingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
@Slf4j
public class SchedulingServiceImpl implements SchedulingService {

    private final ClusterService clusterService;

    public SchedulingServiceImpl(ClusterService clusterService) {
        this.clusterService = clusterService;
    }

    @Override
    public SchedulingResponse selectBestCluster(SchedulingRequest request) {
        List<ClusterStatus> clusters = clusterService.getAllClusterStatus();
        if (clusters == null || clusters.isEmpty()) {
            return new SchedulingResponse(null, 0.0, "no-clusters-available");
        }

        // 计算评分：score = (100 - cpuUsage) + nodeCount * smallFactor
        // 越大越好
        double nodeFactor = 0.001;  // nodeCount对score的影响权重
        Optional<ClusterStatus> bestOpt = clusters.stream()
                .max(Comparator.comparingDouble(c -> (100.0 - c.getCpuUsage()) + c.getNodeCount() * nodeFactor));
        if (!bestOpt.isPresent()){
            return new SchedulingResponse(null,0.0,"no-selection");
        }

        ClusterStatus best = bestOpt.get();
        double score = (100-best.getCpuUsage()+best.getNodeCount()*nodeFactor);
        String reason = "lowest_cpu_then_most_nodes";

        return new SchedulingResponse(best.getClusterName(),score,reason);
    }
}
