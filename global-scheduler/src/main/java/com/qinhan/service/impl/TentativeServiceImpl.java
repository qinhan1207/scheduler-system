package com.qinhan.service.impl;

import com.qinhan.model.TentativeRecord;
import com.qinhan.service.TentativeService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;


/**
 * 模拟全局协调逻辑：
 * - Bridge 上报 tentative 决策（addTentative）
 * - 定时任务每隔几秒扫描并确认部分记录
 */
@Service
public class TentativeServiceImpl implements TentativeService {

    // 用于存储所有 tentative 记录
    private final Map<String, TentativeRecord> recordMap = new ConcurrentHashMap<>();

    // 模拟每个集群的当前 Pod 数（简单版本）
    private final Map<String, Integer> clusterPodCount = new ConcurrentHashMap<>();

    private final int MAX_PODS_PER_CLUSTER = 10000; // 示例阈值

    @Override
    public void addTentative(TentativeRecord record) {
        record.setState("tentative");
        record.setCreatedAt(LocalDateTime.now());
        record.setUpdatedAt(LocalDateTime.now());
        recordMap.put(record.getNamespace() + "/" + record.getPodName(), record);
    }

    @Override
    public List<TentativeRecord> getAllRecords() {
        return new ArrayList<>(recordMap.values());
    }

    @Scheduled(fixedDelay = 5000)
    public void processTentatives() {
        for (TentativeRecord record : recordMap.values()) {
            if (!"tentative".equals(record.getState())) continue;

            String cluster = record.getCluster();
            int currentCount = clusterPodCount.getOrDefault(cluster, 0);

            // 简单的确认规则：如果该集群负载低于上限，则确认
            if (currentCount < MAX_PODS_PER_CLUSTER) {
                clusterPodCount.put(cluster, currentCount + 1);
                record.setState("confirmed");
            } else {
                record.setState("rejected");
                record.setReason("cluster capacity full");
            }
            record.setUpdatedAt(LocalDateTime.now());
        }
    }
}
