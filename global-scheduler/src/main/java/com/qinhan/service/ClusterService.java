package com.qinhan.service;


import com.qinhan.model.ClusterStatus;

import java.util.List;

public interface ClusterService {

    void updateClusterStatus(ClusterStatus status);

    List<ClusterStatus> getAllClusterStatus();
}
