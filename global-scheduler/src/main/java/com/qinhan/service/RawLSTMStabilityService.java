package com.qinhan.service;

import com.qinhan.model.PredictionResult;
import com.qinhan.util.RawLSTMPredictor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 🍂 实验对照组服务：Raw-LSTM (SMA + LSTM)
 * 修正逻辑：
 * 不再使用瞬时值，而是使用"简单滑动窗口平均"(Simple Moving Average)作为输入。
 * 这样能与 EWMA 形成公平对比：一个是朴素平均，一个是自适应加权。
 */
@Slf4j
@Service
public class RawLSTMStabilityService {

    @Value("${experiment.risk-alpha:0.5}")
    private double riskAlpha;

    // 归一化基准
    private static final double MAX_LATENCY_NORM = 300.0;

    // 窗口大小 (例如 5，表示取最近 5 次采集的平均值)
    private static final int WINDOW_SIZE = 15;

    // 状态容器：Key=ClusterName, Value=历史延迟队列
    private final Map<String, Deque<Double>> historyMap = new ConcurrentHashMap<>();

    /**
     * 计算 Raw-LSTM 组的稳定性得分
     *
     * @param clusterName    集群名称
     * @param currentLatency 当前原始延迟
     * @return 0-100 的分数
     */
    public double calculateScore(String clusterName, double currentLatency) {

        // 1. 【新增】计算滑动窗口平均值 (SMA)
        double avgLatency = calculateSimpleMovingAverage(clusterName, currentLatency);

        // 2. 调用工具类 (Port 5002)
        // 🔥 注意：这里传入的是算出来的 avgLatency，而不是 currentLatency
        PredictionResult result = RawLSTMPredictor.predict(clusterName, avgLatency);

        // 3. 获取风险概率
        double riskProb = 0.0;
        if (result.isSuccess()) {
            riskProb = result.getProbability();
        } else {
            // 降级处理
            riskProb = 0.0;
        }

        // 4. 计算 Cost
        // 使用平均值进行 Cost 计算，这样更稳健
        double normAvg = Math.min(1.0, avgLatency / MAX_LATENCY_NORM);
        double cost = (1 - riskAlpha) * normAvg + (riskAlpha * riskProb);

        // 5. 转换为 0-100 分
        double score = (1.0 - Math.min(1.0, cost)) * 100.0;

        return Math.max(0.0, score);
    }

    /**
     * 内部方法：维护窗口并计算平均值
     */
    private double calculateSimpleMovingAverage(String clusterName, double currentValue) {
        // 获取或创建该集群的队列
        // computeIfAbsent 保证线程安全地创建
        Deque<Double> window = historyMap.computeIfAbsent(clusterName, k -> new ArrayDeque<>(WINDOW_SIZE));

        // 必须同步块，防止并发修改同一个 Deque
        synchronized (window) {
            // 如果满了，移除最旧的
            if (window.size() >= WINDOW_SIZE) {
                window.pollFirst();
            }
            // 加入最新的
            window.addLast(currentValue);

            // 计算平均值
            double sum = 0.0;
            for (Double val : window) {
                sum += val;
            }
            return sum / window.size();
        }
    }
}