package com.qinhan.client;

import com.qinhan.model.ClusterStatus;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Component
public class GlobalSchedulerClient {

    @Value("${global.scheduler.url:http://localhost:8080}")
    private String globalSchedulerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    /**
     * 向 GC 上报集群状态
     */
    public void sendClusterStatus(ClusterStatus status) {
        try {
            String url = globalSchedulerUrl + "/api/clusters/report";
            ResponseEntity<String> response = restTemplate.postForEntity(url, status, String.class);
            log.debug("📤 集群 [{}] 状态上报响应: {}", status.getClusterName(), response.getBody());
        } catch (Exception e) {
            log.error("❌ 上报集群 [{}] 状态失败: {}", status.getClusterName(), e.getMessage());
        }
    }
}
