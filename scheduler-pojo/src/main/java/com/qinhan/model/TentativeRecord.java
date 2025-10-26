package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用于保存 tentative（暂定） 调度结果的记录
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TentativeRecord {
    private String recordId;           // 记录唯一ID
    private String workloadName;       // 工作负载名称
    private String namespace;          // 命名空间
    private String recommendedCluster; // 推荐集群
    private double score;              // 推荐分数
    private String reason;             // 推荐理由
    private String state;              // 状态: PENDING/CONFIRMED/REJECTED
    private LocalDateTime createdAt;   // 创建时间
    private LocalDateTime updatedAt;   // 更新时间
    private String decisionReason;     // 最终决策理由
}