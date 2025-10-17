package com.qinhan.util;

import com.qinhan.properties.K8sClientProperties;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.FileReader;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * 通用 K8sClient 工具类 - 支持多 kubeconfig，兼容实验环境
 * ✅ 默认跳过 SSL 验证
 * ✅ 可多线程安全缓存多个集群连接
 */
@Slf4j
@Component
public class K8sClientUtil {

    private final K8sClientProperties properties;

    // 缓存多个 kubeconfig 对应的 ApiClient，避免重复创建连接
    private static final Map<String, ApiClient> clientCache = new ConcurrentHashMap<>();

    public K8sClientUtil(K8sClientProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取指定 kubeconfig 的 ApiClient（若不存在则新建）
     */
    public ApiClient getClient(String kubeconfigPath) {
        return clientCache.computeIfAbsent(kubeconfigPath, path -> {
            try {
                log.info("🔧 加载 kubeconfig: {}", path);
                FileReader reader = new FileReader(path);
                KubeConfig kc = KubeConfig.loadKubeConfig(reader);
                ApiClient client = ClientBuilder.kubeconfig(kc)
                        .setVerifyingSsl(false) // ⚠️ 实验环境跳过证书校验
                        .build();

                client.setReadTimeout(0); // 永不超时，支持 watch
                Configuration.setDefaultApiClient(client);

                log.info("✅ ApiClient 创建成功, basePath={}", client.getBasePath());
                return client;

            } catch (Exception e) {
                log.error("❌ 创建 ApiClient 失败: {}", path, e);
                throw new RuntimeException("K8s ApiClient 创建失败: " + path, e);
            }
        });
    }

    /**
     * 获取默认 kubeconfig 的 ApiClient
     */
    public ApiClient getDefaultClient() {
        String defaultPath = properties.getDefaultKubeconfigPath();
        if (defaultPath == null || defaultPath.isEmpty()) {
            throw new IllegalStateException("未配置默认 kubeconfig 路径 (k8s.client.default-kubeconfig-path)");
        }
        return getClient(defaultPath);
    }
}
