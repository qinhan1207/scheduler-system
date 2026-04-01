package com.qinhan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 通用 HTTP 客户端配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "http.client")
public class HttpClientProperties {
    private int connectTimeout = 5000;
    private int readTimeout = 10000;
}
