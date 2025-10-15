package com.qinhan.util;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;
import lombok.extern.slf4j.Slf4j;
import okhttp3.OkHttpClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.net.ssl.*;
import java.io.FileReader;
import java.security.cert.X509Certificate;
import java.util.concurrent.TimeUnit;

@Slf4j
public class K8sClientUtil {

    public static ApiClient createApiClient(String kubeconfigPath) throws Exception {
        log.info("🔧 开始创建 ApiClient，配置文件: {}", kubeconfigPath);

        // 1. 加载 kubeconfig 文件
        KubeConfig kubeConfig = KubeConfig.loadKubeConfig(new FileReader(kubeconfigPath));

        log.info("📡 目标服务器: {}", kubeConfig.getServer());
        log.info("🔑 当前上下文: {}", kubeConfig.getCurrentContext());

        // 2. 创建信任所有证书的 SSL 配置
        final TrustManager[] trustAllCerts = new TrustManager[]{
                new X509TrustManager() {
                    @Override
                    public void checkClientTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public void checkServerTrusted(X509Certificate[] chain, String authType) {}
                    @Override
                    public X509Certificate[] getAcceptedIssuers() {
                        return new X509Certificate[]{};
                    }
                }
        };
        final SSLContext sslContext = SSLContext.getInstance("TLS");
        sslContext.init(null, trustAllCerts, new java.security.SecureRandom());
        final SSLSocketFactory sslSocketFactory = sslContext.getSocketFactory();

        // 3. 先用标准方式创建客户端
        ApiClient client = ClientBuilder.kubeconfig(kubeConfig).build();

        // 4. 然后修改其 HttpClient 配置
        OkHttpClient.Builder httpClientBuilder = client.getHttpClient().newBuilder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .sslSocketFactory(sslSocketFactory, (X509TrustManager) trustAllCerts[0])
                .hostnameVerifier((hostname, session) -> true);

        client.setHttpClient(httpClientBuilder.build());

        log.warn("⚠️ SSL 证书验证已禁用");
        log.info("✅ ApiClient 创建完成, BasePath: {}", client.getBasePath());
        return client;
    }
}