package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * ForecastResult (预测结果封装)
 * 对应论文：算法输出向量 Output Vector <μ, σ, v>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ForecastResult {
    
    /** * 预测均值 (Predicted Mean, μ_t) 
     * 作用：作为平滑后的网络延迟指标，用于可视化展示趋势。
     */
    private double predMean;

    /** * 预测偏差 (Predicted Deviation, σ_t)
     * 作用：表示当前预测的不确定性范围。
     */
    private double predDeviation;

    /** * 相对波动率 (Relative Volatility, v = σ / μ)
     * 作用：SCI 论文核心指标，用于量化网络风险并触发熔断。
     */
    private double volatility;

    /**
     * LSTM 预测出的故障概率 (0.0 ~ 1.0)
     */
    private double riskProbability;
}