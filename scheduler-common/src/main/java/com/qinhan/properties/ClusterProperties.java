package com.qinhan.properties;


import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "lsa.clusters")
@Data
public class ClusterProperties {
    private String name;
    private String kubeconfig;
    private String simulatedProfile = "normal";
}
