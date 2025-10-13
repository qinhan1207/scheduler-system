package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * 调度请求模型：可以携带 pod 信息（名称、命名空间、labels）与资源需求（可选）。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SchedulingRequest {
    private String podName;
    private String namespace;
    private Map<String, String> labels;
    /**
     * 简化资源需求表示（单位：mCPU / MiB），可为空
     * 例如: {"cpu": "500", "memory":"256"}
     */
    private Map<String, String> resourceRequests;
}