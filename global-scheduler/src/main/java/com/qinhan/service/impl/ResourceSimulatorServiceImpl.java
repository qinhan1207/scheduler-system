package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ResourceSimulatorService;
import com.qinhan.util.ValueFluctuator;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 模拟 CPU / 内存动态波动的实现类
 */
@Slf4j
@Service
public class ResourceSimulatorServiceImpl implements ResourceSimulatorService {

    /** 记录上一次模拟的数值，保持波动连续性 */
    private final ConcurrentHashMap<String, ClusterStatus> lastStatusMap = new ConcurrentHashMap<>();

    @Override
    public ClusterStatus enrichDynamicMetrics(ClusterStatus rawStatus) {
        if (rawStatus == null) {
            log.warn("⚠️ 收到空集群状态，跳过模拟。");
            return null;
        }

        String clusterName = rawStatus.getClusterName();
        ClusterStatus last = lastStatusMap.get(clusterName);

        double cpu = rawStatus.getCpuUsage();
        double mem = rawStatus.getMemoryUsage();

        // 如果当前数据缺失或为0，则生成模拟值
        if (cpu <= 0) {
            cpu = (last != null)
                    ? ValueFluctuator.fluctuate(last.getCpuUsage(), 3, 10, 90)
                    : ThreadLocalRandom.current().nextDouble(20, 80);
        }
        if (mem <= 0) {
            mem = (last != null)
                    ? ValueFluctuator.fluctuate(last.getMemoryUsage(), 4, 15, 95)
                    : ThreadLocalRandom.current().nextDouble(30, 85);
        }

        // 更新数值
        rawStatus.setCpuUsage(cpu);
        rawStatus.setMemoryUsage(mem);

        // 缓存当前状态
        lastStatusMap.put(clusterName, rawStatus);

        log.debug("🔄 模拟补全 -> 集群={} | CPU={}% | MEM={}%",
                clusterName,
                String.format("%.2f", cpu),
                String.format("%.2f", mem));

        return rawStatus;
    }
}
