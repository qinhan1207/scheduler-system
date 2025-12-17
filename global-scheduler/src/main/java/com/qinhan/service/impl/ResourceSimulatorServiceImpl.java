package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ResourceSimulatorService;
import org.springframework.stereotype.Service;

/**
 * 资源模拟已废弃，直接透传状态。
 */
@Service
public class ResourceSimulatorServiceImpl implements ResourceSimulatorService {

    @Override
    public ClusterStatus enrichDynamicMetrics(ClusterStatus rawStatus) {
        return rawStatus;
    }
}
