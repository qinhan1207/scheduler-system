package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 用于保存 tentative（暂定） 调度结果的记录
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TentativeRecord {
    private String podName;
    private String namespace;
    private String cluster;
    private double score;
    private String reason;
    private String state;  // tentative / confirmed / rejected
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}