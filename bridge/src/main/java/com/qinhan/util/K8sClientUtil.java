package com.qinhan.util;

import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.util.ClientBuilder;
import io.kubernetes.client.util.KubeConfig;

import java.io.FileReader;

/**
 * 封装 Kubernetes Java Client 初始化逻辑
 */
public class K8sClientUtil {

    public static ApiClient createApiClient(String kubeconfigPath) throws Exception {
        FileReader reader = new FileReader(kubeconfigPath);
        KubeConfig kubeConfig = KubeConfig.loadKubeConfig(reader);
        return ClientBuilder.kubeconfig(kubeConfig).build();
    }
}