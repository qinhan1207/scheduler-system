package com.qinhan.util;

import com.qinhan.model.EwmaFeatureVector;
import com.qinhan.model.PredictionResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class LSTMPredictor {

    // ⚠️ 确保 SSH 隧道 (ssh -L 5001:127.0.0.1:5001 ...) 已经开启
    private static final String SERVER_URL = "http://127.0.0.1:5001/predict";
    private static final String WINDOW_SERVER_URL = "http://127.0.0.1:5001/predict/window";

    /**
     * 调用 LSTM 模型进行预测
     * 🔥 修改：增加了 clusterName 参数，用于服务端隔离缓冲区
     * * @param clusterName 集群名称 (e.g., "member1")
     * @param mu          Dual-EWMA 均值 (原始值，不要归一化)
     * @param sigma       Dual-EWMA 波动率 (原始值，不要归一化)
     * @param volatility  变异系数 (sigma / mu)
     * @return PredictionResult 对象，包含是否故障和概率值
     */
    public static PredictionResult predict(String clusterName, double mu, double sigma, double volatility) {
        try {
            // 1. 构建 JSON 字符串
            // 🔥 关键修改：加入 "cluster_name" 字段
            String jsonInput = String.format(
                    "{\"cluster_name\": \"%s\", \"mu\": %f, \"sigma\": %f, \"volatility\": %f}",
                    clusterName, mu, sigma, volatility
            );

            return postJson(SERVER_URL, clusterName, jsonInput);

        } catch (Exception e) {
            // 网络不通（如 SSH 隧道断了）
            return PredictionResult.failure("Connection Failed: " + e.getMessage());
        }
    }

    /**
     * 按窗口推理（主路径）
     * Python 侧接口接收二维序列：(windowSize, 3)，每个点为 [mu, sigma, volatility]。
     */
    public static PredictionResult predictByWindow(String clusterName, List<EwmaFeatureVector> ftWindow) {
        if (ftWindow == null || ftWindow.isEmpty()) {
            return PredictionResult.failure("Empty ftWindow");
        }

        try {
            StringBuilder windowJson = new StringBuilder("[");
            for (int i = 0; i < ftWindow.size(); i++) {
                EwmaFeatureVector point = ftWindow.get(i);
                if (point == null) {
                    continue;
                }
                if (windowJson.length() > 1) {
                    windowJson.append(',');
                }
                windowJson.append(String.format("[%f,%f,%f]",
                        point.getMeanFeature(),
                        point.getDeviationFeature(),
                        point.getVolatility()));
            }
            windowJson.append(']');

            String jsonInput = String.format(
                    "{\"cluster_name\": \"%s\", \"ft_window\": %s}",
                    clusterName,
                    windowJson
            );

            return postJson(WINDOW_SERVER_URL, clusterName, jsonInput);

        } catch (Exception e) {
            return PredictionResult.failure("Connection Failed: " + e.getMessage());
        }
    }

    private static PredictionResult postJson(String serverUrl, String clusterName, String jsonInput) throws Exception {
        URL url = new URL(serverUrl);

        // 关键：显式使用 NO_PROXY，防止被 VPN/代理拦截
        HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);

        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("X-Cluster-Name", clusterName);
        conn.setDoOutput(true);
        conn.setConnectTimeout(1000); // 1秒超时，快速失败
        conn.setReadTimeout(1000);

        try (OutputStream os = conn.getOutputStream()) {
            byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
            os.write(input, 0, input.length);
        }

        int code = conn.getResponseCode();
        if (code != 200 && code != 202) {
            return PredictionResult.failure("HTTP Error: " + code);
        }

        StringBuilder response = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line.trim());
            }
        }

        return parseJson(response.toString());
    }

    /**
     * 简单的 JSON 解析器
     * 解析形如: {"code":200, "is_fault":true, "prob":0.9823, "msg":"OK", "cluster":"member1"}
     */
    private static PredictionResult parseJson(String json) {
        try {
            // 去除所有空格和换行，方便解析
            String cleanJson = json.replace(" ", "").replace("\n", "").replace("\"", "");

            // 提取 is_fault
            boolean isFault = cleanJson.contains("is_fault:true");

            // 提取 prob
            double prob = 0.0;
            int probIdx = cleanJson.indexOf("prob:");
            if (probIdx != -1) {
                int start = probIdx + 5; // "prob:".length()
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

            // 提取 msg (简单提取，不做严格处理)
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