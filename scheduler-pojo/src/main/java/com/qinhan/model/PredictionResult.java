package com.qinhan.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 封装 LSTM 模型的预测结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PredictionResult {
    private boolean isSuccess;  // 请求是否成功
    private boolean isFault;    // 模型是否判定为故障 (Boolean结论)
    private double probability; // 故障概率 (0.0 - 1.0)
    private String message;     // 服务端返回的消息 (例如 "Buffering...")

    // 失败时的静态工厂方法
    public static PredictionResult failure(String errorMsg) {
        return new PredictionResult(false, false, 0.0, errorMsg);
    }



    @Override
    public String toString() {
        return String.format("PredictionResult[success=%b, fault=%b, prob=%.4f, msg=%s]", 
            isSuccess, isFault, probability, message);
    }
}