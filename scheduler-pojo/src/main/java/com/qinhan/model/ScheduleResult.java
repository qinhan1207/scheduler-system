package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.util.List;
import java.util.Map;

/**
 * ScheduleResult
 * --------------------------------------------------
 * 用于记录每次 Karmada 控制平面调度任务（ResourceBinding）的结果。
 * 由 Global Scheduler 监听 Karmada 控制平面的 ResourceBinding（RB）事件生成。
 *
 * 数据用途：
 *  - 存储调度历史，用于分析和可视化。
 *  - 为第三阶段“自学习模型”提供训练标签（label）。
 *
 * 数据来源：
 *  - Karmada 控制平面的 ResourceBinding 对象（watch 或 list 操作）
 *  - 每次调度、重新调度、失败事件都会触发更新。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ScheduleResult {

    // ================== 基础标识信息 ==================
    /** 数据库主键（自增ID） */
    private Long id;

    /** ResourceBinding 名称（Karmada 自动生成） */
    private String bindingName;

    /** 绑定所在的命名空间（例如 default） */
    private String namespace;

    /** 资源类型（Deployment / Job / StatefulSet / CronJob 等） */
    private String workloadKind;

    /** 原始 workload 名称（例如 nginx-deployment） */
    private String workloadName;

    // ================== 调度结果信息 ==================
    /**
     * 被 Karmada 选中的成员集群列表
     * 例如 ["kwok-cluster01", "kwok-cluster02"]
     */
    private List<String> selectedClusters;

    /**
     * 每个集群分配的副本数（可选）
     * 例如 {"kwok-cluster01": 2, "kwok-cluster02": 2}
     */
    private Map<String, Integer> replicasDistribution;

    /** 调度状态：Success / Rescheduled / Failed */
    private String schedulingStatus;

    /** 调度状态原因，例如 "ScheduleBindingSucceed" */
    private String schedulingReason;

    /** 调度事件的发生时间戳 */
    private long schedulingTimestamp;

    // ================== 监督学习标签 ==================
    /**
     * 调度结果标签：
     *   1 = 调度成功
     *   0 = 调度失败
     *   （可用于模型训练时的监督信号）
     */
    private Integer resultLabel;

    /** 可选字段：调度失败原因说明（便于异常分析） */
    private String failureMessage;

    // ================== 元数据 ==================
    /** 记录创建时间（入库时间） */
    private Instant createdAt;

    /** 记录最后更新时间（若重复调度可更新） */
    private Instant updatedAt;
}
