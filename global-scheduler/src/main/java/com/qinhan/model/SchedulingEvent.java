package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Date;
import java.util.List;

/**
 * Bridge监听到的ResourceBinding事件
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingEvent {
    private String name;                    // ResourceBinding名称
    private String namespace;               // 命名空间
    private String eventType;               // ADDED/MODIFIED/DELETED
    private Date timestamp;                 // 事件时间
    
    // 被调度的资源信息
    private String workloadKind;           // Deployment/Pod/Job等
    private String workloadName;           // 工作负载名称
    private String workloadApiVersion;     // api版本
    
    // 调度决策信息
    private List<String> targetClusters;   // 目标集群列表
    private String propagationPolicy;      // 使用的传播策略
    
    // 状态信息
    private boolean scheduled;             // 是否已调度
    private boolean fullyApplied;          // 是否完全应用
    private String scheduleResult;         // 调度结果描述
}