package com.qinhan.util;

import lombok.extern.slf4j.Slf4j;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;

/**
 * 📊 实验数据导出工具类
 * 作用：将实验过程中的关键数据写入 CSV 文件，方便直接用 Excel 画图。
 * 文件位置：项目根目录/experiment_results.csv
 */
@Slf4j
public class ExperimentDataLogger {

    // 文件名
    private static final String CSV_FILE = "experiment_results.csv";
    
    // 时间格式
    private static final SimpleDateFormat TIME_FMT = new SimpleDateFormat("HH:mm:ss");

    /**
     * 写入一行实验数据 (仅追加)
     *
     * @param clusterName 集群名
     * @param timestamp   时间戳
     * @param latency     真实延迟 (Ground Truth)
     * @param baseline    Baseline分数
     * @param rawLstm     Raw-LSTM分数
     * @param statOnly    Stat-Only分数
     * @param proposed    Proposed分数
     */
    public static synchronized void log(String clusterName, long timestamp, double latency,
                                        double baseline, double rawLstm, double statOnly, double proposed) {
        
        File file = new File(CSV_FILE);
        boolean isNewFile = !file.exists();

        try (BufferedWriter writer = new BufferedWriter(new FileWriter(file, true))) {
            
            // 1. 如果是新文件，先写表头
            if (isNewFile) {
                writer.write("TimeStr,Timestamp,Cluster,Real_Latency,Baseline,Raw_LSTM,Stat_Only,Proposed");
                writer.newLine();
            }

            // 2. 准备数据行 (CSV格式: 用逗号分隔)
            String timeStr = TIME_FMT.format(new Date(timestamp));
            
            String line = String.format("%s,%d,%s,%.2f,%.2f,%.2f,%.2f,%.2f",
                    timeStr,
                    timestamp,
                    clusterName,
                    latency,
                    baseline,
                    rawLstm,
                    statOnly,
                    proposed
            );

            // 3. 写入文件
            writer.write(line);
            writer.newLine(); // 换行

        } catch (IOException e) {
            log.error("❌ 写入实验数据 CSV 失败: {}", e.getMessage());
        }
    }
}