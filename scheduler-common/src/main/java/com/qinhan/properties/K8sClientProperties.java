package com.qinhan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 全局 K8s 客户端配置
 */
@Data
@Component
@ConfigurationProperties(prefix = "k8s.client")
public class K8sClientProperties {
    private boolean verifySsl = false;         // 是否启用 SSL 校验（默认关闭）
    private int readTimeout = 0;               // 读取超时，0 表示无限
    private String defaultKubeconfigPath;      // 默认 kubeconfig 路径
}
