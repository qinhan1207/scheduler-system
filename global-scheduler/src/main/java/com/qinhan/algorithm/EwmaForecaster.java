package com.qinhan.algorithm;

import com.qinhan.model.EwmaState;
import com.qinhan.model.ForecastResult;
import com.qinhan.model.PredictionResult;
import com.qinhan.util.LSTMPredictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * EWMA 核心算法引擎 (SCI 增强版)
 * 对应论文创新点：同时追踪 "趋势(Mean)" 和 "波动(Deviation)" 的双重 EWMA 模型
 */
@Slf4j
@Component
public class EwmaForecaster {

    // 状态容器：Key = "clusterName:metricName"
    private final Map<String, EwmaState> stateMap = new ConcurrentHashMap<>();

    // 基础平滑因子 (超参数)
    private static final double BASE_ALPHA = 0.3; // 用于均值 (Mean)
    private static final double BETA = 0.1;       // 用于偏差 (Deviation)

    /**
     * 核心预测方法
     *
     * @param clusterName  集群名
     * @param metricName   指标名 (e.g., "latency")
     * @param currentValue 当前真实值 (Observation)
     * @return 包含均值和波动率的完整结果
     */
    public ForecastResult predict(String clusterName, String metricName, double currentValue) {
        String key = clusterName + ":" + metricName;

        // 1. 获取或创建该集群的专属状态 (原子操作)
        EwmaState state = stateMap.computeIfAbsent(key, k -> new EwmaState());

        // 2. 冷启动初始化逻辑
        if (!state.isInitialized()) {
            state.setMean(currentValue);
            state.setDeviation(0.0);
            state.setInitialized(true);
            // 返回初始结果
            return ForecastResult.builder()
                    .predMean(currentValue)
                    .predDeviation(0.0)
                    .volatility(0.0)
                    .build();
        }

        // 3. 计算当前误差 (Error)
        double lastMean = state.getMean();
        double error = Math.abs(currentValue - lastMean);

        // 4. 自适应 Alpha 计算 (Adaptive Weighting)
        double adaptiveAlpha = calculateAdaptiveAlpha(error, lastMean);

        // ============================================================
        // 5. 双重 EWMA 更新 (Dual-EWMA Update)
        // ============================================================

        // A. 更新偏差 (Deviation Update): σ_t = β * |Error| + (1 - β) * σ_t-1
        double newDeviation = BETA * error + (1 - BETA) * state.getDeviation();

        // B. 更新均值 (Mean Update): μ_t = α * Y_t + (1 - α) * μ_t-1
        double newMean = adaptiveAlpha * currentValue + (1 - adaptiveAlpha) * lastMean;

        // 6. 更新内部记忆状态
        state.setMean(newMean);
        state.setDeviation(newDeviation);

        // 7. 计算相对波动率 (Relative Volatility)
        // 防止除以 0 异常
        double volatility = (newMean > 0.0001) ? (newDeviation / newMean) : 0.0;

        // 打印算法层日志 (用于论文数据分析)
        // 先定义好单位，避免在 log 里写三元表达式，太乱
        String unit = metricName.equals("latency") ? "ms" : "% ";

        // 执行日志打印
        log.info("🔮 [算法] [{}] [{}] 真实:{}{} -> 预测:{}{} (±{}) | 波动:{}% | α:{}",
                clusterName,
                metricName,
                // 1. 真实值 ( %6.2f 表示总宽6位，小数点后2位，自动右对齐 )
                String.format("%6.2f", currentValue),
                unit,
                // 2. 预测值
                String.format("%6.2f", newMean),
                unit,
                // 3. 偏差 (保留2位)
                String.format("%.2f", newDeviation),
                // 4. 波动率 (转成百分比字符串)
                String.format("%.1f", volatility * 100),
                // 5. Alpha (保留2位)
                String.format("%.2f", adaptiveAlpha)
        );

        // 🔍【新增】记录即将调用LSTM预测的集群信息
        log.info("🚀 [LSTM调用] 集群=[{}] 指标=[{}] 准备调用LSTM模型进行故障预测...", clusterName, metricName);
        
        // 调用LSTM并获取概率
        PredictionResult lstmResult = LSTMPredictor.predict(newMean, newDeviation, volatility);
        double probability = lstmResult.getProbability();
        
        // 📊【优化】统一格式化LSTM预测结果日志，包含集群标识
        log.info("🧠 [LSTM结果] 集群=[{}] 指标=[{}] | 是否故障={} | 故障概率={}% | 信息=[{}]",
                clusterName,
                metricName,
                lstmResult.isFault() ? "是" : "否",
                String.format("%.2f", probability * 100),
                lstmResult.getMessage()
        );


        // === 🔥🔥🔥 新增：直接落盘到 CSV 🔥🔥🔥 ===
        // 这一行代码，直接把特征导出，供 LSTM 训练使用
//        TrainingDataCollector.record(
//                clusterName,
//                metricName,
//                currentValue,   // Raw Latency
//                newMean,        // Mu
//                newDeviation,   // Sigma
//                volatility,     // V
//                adaptiveAlpha   // Alpha (留着分析用)
//        );

        // 8. 返回结果对象
        return ForecastResult.builder()
                .predMean(newMean)
                .predDeviation(newDeviation)
                .volatility(volatility)
                .riskProbability(probability)
                .build();
    }

    /**
     * 自适应 Alpha 计算
     * 逻辑：误差越大，Alpha 越大（响应越快）；误差越小，Alpha 越小（抗噪越强）。
     */
    private double calculateAdaptiveAlpha(double error, double lastValue) {
        double errorRatio = (Math.abs(lastValue) < 0.0001) ? 0 : error / Math.abs(lastValue);
        // 基础 0.3 + 误差增益，限制在 [0.1, 0.8] 区间
        double alpha = BASE_ALPHA + (errorRatio * 0.5);
        return Math.max(0.1, Math.min(0.8, alpha));
    }
}