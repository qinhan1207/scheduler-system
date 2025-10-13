package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ClusterService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class ClusterServiceImpl implements ClusterService {

    private final ConcurrentHashMap<String, ClusterStatus> clusterMap = new ConcurrentHashMap<>();

    @Override
    public void updateClusterStatus(ClusterStatus status) {
        clusterMap.put(status.getClusterName(), status);
    }

    @Override
    public List<ClusterStatus> getAllClusterStatus() {
        return new ArrayList<>(clusterMap.values());
    }
}