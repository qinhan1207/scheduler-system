package com.qinhan.service;

public interface BridgeService {



    /**
     * 启动ResourceBinding监听
     */
    void startWatch();

    /**
     * 停止监听
     */
    void stopWatch();

    /**
     * 测试连接
     */
    boolean testConnection();
}