package com.qinhan.service;

public interface ReportService {
    /**
     * 定时采集并上报所有集群状态
     */
    void reportAllClusters();
}
