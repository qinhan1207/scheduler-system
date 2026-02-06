package com.qinhan.util; // 记得改包名

import lombok.extern.slf4j.Slf4j;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.time.Instant;

@Slf4j
public class TrainingDataCollector {

    // 💾 文件会生成在项目根目录下，文件名叫 training_dataset.csv
    private static final String FILE_PATH = "training_dataset.csv";
    private static boolean isHeaderWritten = false;

    /**
     * 核心埋点方法
     * synchronized 保证多线程并发（因为你是 parallelStream）时不会写乱
     */
    public static synchronized void record(String clusterName, String metricType,
                                           double rawValue, double mu, double sigma, double volatility, double alpha) {

        // 只收集 latency 指标用于 LSTM 训练 (loss 指标暂时不需要喂给 LSTM)
        if (!"latency".equalsIgnoreCase(metricType)) {
            return;
        }

        // 只收集 member3 的数据 (作为正样本和故障样本)
        if (!"member3".equalsIgnoreCase(clusterName)) {
            return;
        }

        try (PrintWriter writer = new PrintWriter(new FileWriter(FILE_PATH, true))) {
            // 1. 如果是第一次写，先写表头
            if (!isHeaderWritten) {
                // 表头对应 Python 训练脚本的输入
                writer.println("Timestamp,Raw_Latency,Feat_Mu,Feat_Sigma,Feat_Volatility,Alpha");
                isHeaderWritten = true;
                log.info("📂 [数据采集] CSV 文件已创建: {}", FILE_PATH);
            }

            // 2. 写入数据行
            // 格式: 时间戳, 真实值, 预测均值(Mu), 预测偏差(Sigma), 波动率, Alpha
            writer.printf("%d,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                    Instant.now().toEpochMilli(),
                    rawValue,
                    mu,
                    sigma,
                    volatility,
                    alpha
            );

        } catch (IOException e) {
            log.error("❌ 写入训练数据失败", e);
        }
    }
}