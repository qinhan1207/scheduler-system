package com.qinhan.service;

import com.qinhan.model.SchedulingEvent;

import java.util.List;

/**
 * 处理从bridge接收过来的信息
 */
public interface BridgeEventService {
    
    /**
     * 处理Bridge上报的ResourceBinding事件
     */
    void processBridgeEvent(SchedulingEvent event);
    
    /**
     * 获取所有Bridge事件记录
     */
    List<SchedulingEvent> getAllBridgeEvents();
    
    /**
     * 根据工作负载名称查找事件
     */
    List<SchedulingEvent> getEventsByWorkload(String workloadName);
}