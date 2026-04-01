package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * EwmaFeatureVector
 * 对应论文中的单步特征向量 f_t = <mu, sigma, volatility>。
 * 仅表示 EWMA 输出，不承载 LSTM 风险概率。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EwmaFeatureVector {
    
    /**
     * 特征均值 (Mean Feature, μ_t)
     * 作用：作为平滑后的网络延迟特征。
     */
    private double meanFeature;

    /**
     * 特征偏差 (Deviation Feature, σ_t)
     * 作用：表示当前时刻的波动偏差特征。
     */
    private double deviationFeature;

    /** * 相对波动率 (Relative Volatility, v = σ / μ)
     * 作用：SCI 论文核心指标，用于量化网络风险并触发熔断。
     */
    private double volatility;

}