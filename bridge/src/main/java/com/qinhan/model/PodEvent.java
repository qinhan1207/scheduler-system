package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 用于封装监听到的 Pod 事件（或 Work 对象事件）
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PodEvent {
    private String name;
    private String namespace;
    private String phase;
}