package com.qinhan.client;

import com.qinhan.model.SchedulingEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * 全局调度器客户端 - 负责与GS服务通信
 */
@Slf4j
@Component
public class GlobalSchedulerClient {

    @Value("${global.scheduler.url}")
    private String globalSchedulerUrl;

    private final RestTemplate restTemplate;

    public GlobalSchedulerClient() {
        this.restTemplate = new RestTemplate();
    }

    /**
     * 发送调度事件给Global Scheduler
     */
    public void sendSchedulingEvent(SchedulingEvent event) {
        try {
            String url = globalSchedulerUrl + "/api/bridge/events";
            log.debug("🔄 发送事件到GS: {}", url);
            
            ResponseEntity<String> response = restTemplate.postForEntity(url, event, String.class);
            
            log.info("📤 成功发送Bridge事件给GS: {} - 响应: {}", event.getName(), response.getBody());
            
        } catch (Exception e) {
            log.error("❌ 发送Bridge事件给GS失败: {}", event.getName(), e);
        }
    }
}