package com.qinhan.service;


import com.qinhan.model.ClusterStatus;

import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 决策类
 */
public class ClusterSelector {

    /**
     * 在候选集群（names）中，按 healthScore 降序选 topK
     * 可加入 tie-breaker：较少 podCount 优先
     */
    public static List<String> selectTopKByHealth(List<String> candidateNames, 
                                                  java.util.function.Function<String, ClusterStatus> statusProvider,
                                                  int topK) {
        return candidateNames.stream()
                .map(name -> statusProvider.apply(name)) // may be null
                .filter(s -> s != null)
                .sorted(Comparator.comparingDouble(ClusterStatus::getHealthScore).reversed()
                        .thenComparingInt(ClusterStatus::getPodCount))
                .limit(topK)
                .map(ClusterStatus::getClusterName)
                .collect(Collectors.toList());
    }
}
