package com.qinhan.config;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Slf4j
@Configuration
public class KarmadaClientConfig {

    @Value("${karmada.kubeconfig}")
    private String karmadaConfigPath;

    @Bean
    public ApiClient karmadaClient() {
        try {
            // ✅ karmada-config文件路径
            ApiClient client = Config.fromConfig(karmadaConfigPath);

            // ✅ 可选：设置连接超时和线程池大小
            client.setConnectTimeout(10_000);
            client.setReadTimeout(10_000);

            log.info("✅ 成功创建 Karmada ApiClient 连接: {}", client.getBasePath());
            return client;

        } catch (Exception e) {
            log.error("❌ 创建 Karmada ApiClient 失败: {}", e.getMessage());
            throw new RuntimeException("无法初始化 Karmada ApiClient", e);
        }
    }
}
