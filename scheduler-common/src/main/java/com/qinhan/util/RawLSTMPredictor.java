package com.qinhan.util;

import com.qinhan.model.PredictionResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

/**
 * 🔥 专门用于调用 Raw-LSTM (无特征工程版) 的工具类
 * 端口: 5002
 */
public class RawLSTMPredictor {

    // 指向运行 Raw 模型的 Python 服务 (端口 5002)
    private static final String SERVER_URL = "http://127.0.0.1:5002/predict";

    /**
     * 调用 Raw-LSTM 模型进行预测
     * @param clusterName 集群名称 (用于缓冲区隔离)
     * @param currentLatency 当前原始延迟 (Raw Latency)
     * @return 预测结果
     */
    public static PredictionResult predict(String clusterName, double currentLatency) {
        try {
            URL url = new URL(SERVER_URL);

            // 显式使用 NO_PROXY
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setRequestProperty("X-Cluster-Name", clusterName); // Header 也带上，双重保险
            conn.setDoOutput(true);
            conn.setConnectTimeout(1000); // 1秒超时
            conn.setReadTimeout(1000);

            // 构建 JSON
            // ⚠️ 注意：我们将 currentLatency 放入 "mu" 字段发送
            // 因为 Python 端的映射逻辑已经配置为：如果模型需要 latency，就去 "mu" 字段找。
            // 这样是为了复用逻辑，避免 Python 端改动太大。
            String jsonInput = String.format(
                    "{\"cluster_name\": \"%s\", \"mu\": %f}",
                    clusterName, currentLatency
            );

            // 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 检查状态码
            int code = conn.getResponseCode();
            if (code != 200 && code != 202) {
                return PredictionResult.failure("HTTP Error: " + code);
            }

            // 读取响应
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
            }

            return parseJson(response.toString());

        } catch (Exception e) {
            // Raw 服务挂了不应该影响主流程，返回一个默认的安全值
            return PredictionResult.failure("Raw-LSTM Connection Failed: " + e.getMessage());
        }
    }

    /**
     * JSON 解析器 (复用逻辑)
     */
    private static PredictionResult parseJson(String json) {
        try {
            String cleanJson = json.replace(" ", "").replace("\n", "").replace("\"", "");
            boolean isFault = cleanJson.contains("is_fault:true");
            double prob = 0.0;
            int probIdx = cleanJson.indexOf("prob:");
            if (probIdx != -1) {
                int start = probIdx + 5;
                int endComma = cleanJson.indexOf(",", start);
                int endBrace = cleanJson.indexOf("}", start);
                int end = endComma;
                if (end == -1 || (endBrace != -1 && endBrace < endComma)) {
                    end = endBrace;
                }
                if (end != -1) {
                    try {
                        prob = Double.parseDouble(cleanJson.substring(start, end));
                    } catch (NumberFormatException e) {
                        prob = 0.0;
                    }
                }
            }
            String msg = "OK";
            if (cleanJson.contains("Buffering")) {
                msg = "Buffering...";
            }
            return new PredictionResult(true, isFault, prob, msg);
        } catch (Exception e) {
            return PredictionResult.failure("JSON Parse Error");
        }
    }
}