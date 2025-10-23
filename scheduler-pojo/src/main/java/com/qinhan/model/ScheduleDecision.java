package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * GS模块做出的调度决策
 * 全局调度决策结果类
 * 用于封装每次调度评估的输出结果。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleDecision {

    /** 是否需要重新调度 */
    private boolean needReschedule;

    /** 推荐目标集群名 */
    private String recommendedCluster;

    /** 推荐目标集群的健康分 */
    private double recommendedScore;

    /** 决策理由（评估结论） */
    private String reason;

    /** 对应的 workload 名称 */
    private String workloadName;

    /** workload 所在的命名空间 */
    private String namespace;
}
