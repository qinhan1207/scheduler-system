package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 调度响应：建议的集群与分数/理由
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingResponse {
    private String cluster;     // 推荐的集群名称
    private double score;       // 评分（越大越优）
    private String reason;      // 简要说明（例如 "lowest cpu"）
}