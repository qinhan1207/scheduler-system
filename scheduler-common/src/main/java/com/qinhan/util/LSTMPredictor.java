package com.qinhan.util;

import com.qinhan.model.PredictionResult;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class LSTMPredictor {

    // ⚠️ 确保 SSH 隧道 (ssh -L 5001:127.0.0.1:5001 ...) 已经开启
    private static final String SERVER_URL = "http://127.0.0.1:5001/predict";

    /**
     * 调用 LSTM 模型进行预测
     * * @param mu         Dual-EWMA 均值
     * @param sigma      Dual-EWMA 波动率
     * @param volatility 变异系数 (sigma / mu)
     * @return PredictionResult 对象，包含是否故障和概率值
     */
    public static PredictionResult predict(double mu, double sigma, double volatility) {
        try {
            URL url = new URL(SERVER_URL);

            // 关键：显式使用 NO_PROXY，防止被 VPN/代理拦截
            HttpURLConnection conn = (HttpURLConnection) url.openConnection(Proxy.NO_PROXY);

            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json; utf-8");
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setConnectTimeout(1000); // 1秒超时，快速失败
            conn.setReadTimeout(1000);

            // 1. 构建 JSON 字符串
            String jsonInput = String.format(
                    "{\"mu\": %f, \"sigma\": %f, \"volatility\": %f}",
                    mu, sigma, volatility
            );

            // 2. 发送请求
            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonInput.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            // 3. 检查状态码
            int code = conn.getResponseCode();
            if (code != 200 && code != 202) {
                return PredictionResult.failure("HTTP Error: " + code);
            }

            // 4. 读取响应
            StringBuilder response = new StringBuilder();
            try (BufferedReader br = new BufferedReader(
                    new InputStreamReader(conn.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    response.append(line.trim());
                }
            }

            // 5. 手动解析 JSON (避免引入 Gson/Jackson 依赖)
            return parseJson(response.toString());

        } catch (Exception e) {
            // 网络不通（如 SSH 隧道断了）
            return PredictionResult.failure("Connection Failed: " + e.getMessage());
        }
    }

    /**
     * 简单的 JSON 解析器
     * 解析形如: {"code":200, "is_fault":true, "prob":0.9823, "msg":"OK"}
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