package com.qinhan.util;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import io.kubernetes.client.openapi.Configuration;
import lombok.extern.slf4j.Slf4j;

import java.io.FileReader;

/**
 * K8sClientUtil - 创建 ApiClient，适用于实验环境（跳过 TLS 验证）
 */
@Slf4j
public class K8sClientUtil {

    private static volatile ApiClient client;

    public static ApiClient getClient(String kubeconfigPath) {
        if (client != null) return client;
        synchronized (K8sClientUtil.class) {
            if (client != null) return client;
            try {
                log.info("🔧 加载 kubeconfig: {}", kubeconfigPath);
                FileReader reader = new FileReader(kubeconfigPath);
                KubeConfig kc = KubeConfig.loadKubeConfig(reader);
                client = ClientBuilder.kubeconfig(kc)
                        .setVerifyingSsl(false) // 实验环境跳过证书校验
                        .build();
                // 永远不超时以支持长链接 watch
                client.setReadTimeout(0);
                Configuration.setDefaultApiClient(client);
                log.info("✅ ApiClient 创建成功, basePath={}", client.getBasePath());
                return client;
            } catch (Exception e) {
                log.error("❌ 创建 ApiClient 失败", e);
                throw new RuntimeException(e);
            }
        }
    }
}
