package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EwmaState (EWMA 内部状态)
 * 对应论文：时序预测模型的"记忆单元" (Memory Cell)
 * 作用：维护每个集群/指标的上一时刻状态，用于递归计算。
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class EwmaState {
    
    /** 上一时刻的预测均值 (μ_t-1) */
    private double mean;
    
    /** 上一时刻的预测偏差 (σ_t-1) */
    private double deviation;
    
    /** 是否已完成初始化 (Cold Start Flag) */
    private boolean initialized = false;
}