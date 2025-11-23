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
 * ✅ 支持 In-Cluster 模式 (Pod 内部运行)
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
     * 支持 "in-cluster" 关键字以加载 Pod 内部配置
     */
    public ApiClient getClient(String kubeconfigPath) {
        return clientCache.computeIfAbsent(kubeconfigPath, path -> {
            try {
                ApiClient client;

                // 🔥 新增：In-Cluster 模式支持
                if ("in-cluster".equalsIgnoreCase(path)) {
                    log.info("🔧 使用 In-Cluster 配置加载 K8s Client (Pod内部模式)...");
                    // 自动读取 ServiceAccount Token 和 CA 证书
                    client = ClientBuilder.cluster().build();
                } else {
                    // 原有的文件加载逻辑
                    log.info("🔧 加载 kubeconfig 文件: {}", path);
                    FileReader reader = new FileReader(path);
                    client = ClientBuilder.kubeconfig(KubeConfig.loadKubeConfig(reader)).build();
                }

                // 通用配置：跳过 SSL 验证 (实验环境常用)
                client.setVerifyingSsl(false);
                // 设置读取超时为 0 (无限)，方便 Watch 操作
                client.setReadTimeout(0);

                // 设置为全局默认 Client (可选)
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
            // 如果没有配置默认路径，尝试使用 in-cluster
            log.warn("未配置默认 kubeconfig 路径，尝试使用 In-Cluster 模式");
            return getClient("in-cluster");
        }
        return getClient(defaultPath);
    }
}