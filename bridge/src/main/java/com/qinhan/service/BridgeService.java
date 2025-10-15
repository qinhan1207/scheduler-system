package com.qinhan.service;

public interface BridgeService {
    /**
     * 启动 Work 对象监听器
     */
    void startWatch();

    /**
     * 停止 Work 对象监听器
     */
    void stopWatch();

    /**
     * 检查监听器是否正在运行
     * @return true 如果正在运行，false 否则
     */
    boolean isWatcherRunning();

    /**
     * 获取监听器状态
     * @return 状态字符串
     */
    String getWatcherStatus();

    /**
     * 获取最近的活动日志
     * @return 活动日志信息
     */
    String getRecentActivity();

    /**
     * 手动触发调度检查
     * @param workName Work 名称
     * @param namespace 命名空间
     * @return 调度结果
     */
    boolean triggerScheduling(String workName, String namespace);
}
