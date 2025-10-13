package com.qinhan.service.impl;

import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ReportService;
import com.qinhan.util.HttpClientUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Random;

@Slf4j
@Service
public class ReportServiceImpl implements ReportService {

    @Value("${global.scheduler.url:http://localhost:8080/api/clusters/report}")
    private String globalSchedulerUrl;

    private final Random random = new Random();

    @Override
    @Scheduled(fixedRate = 10000) // 每10秒上报一次
    public void reportClusterStatus() {
        log.info("上报集群数据到global scheduler");
        ClusterStatus status = new ClusterStatus(
                "cluster-" + random.nextInt(20),
                5000,
                random.nextDouble() * 100,
                random.nextDouble() * 100,
                Instant.now().toEpochMilli()
        );

        try {
            String response = HttpClientUtil.postJson(globalSchedulerUrl, status);
            System.out.println("✅ Reported: " + response);
        } catch (Exception e) {
            System.err.println("❌ Failed to report cluster status: " + e.getMessage());
        }
    }
}
