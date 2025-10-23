package com.qinhan.properties;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * LSA多集群配置
 * 从 application.yml 读取 LSA 多集群配置信息
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "lsa.clusters")
public class ClusterProperties {

    private List<ClusterConfig> configs;

    @Data
    public static class ClusterConfig {
        private String name;            // 集群名称
        private String kubeconfigPath;  // kubeconfig 路径
    }
}

