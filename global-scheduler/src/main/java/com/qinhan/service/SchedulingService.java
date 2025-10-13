package com.qinhan.service;

import com.qinhan.model.SchedulingRequest;
import com.qinhan.model.SchedulingResponse;


public interface SchedulingService {
    /**
     * 根据传入的调度请求，选出一个最优集群（或返回 null 表示未找到）
     */
    SchedulingResponse selectBestCluster(SchedulingRequest request);
}