package com.qinhan;

import com.qinhan.properties.ClusterProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;


@EnableScheduling
@SpringBootApplication
@EnableConfigurationProperties(ClusterProperties.class)
public class LocalSchedulerAgentApplication  {

    public static void main(String[] args) {
        SpringApplication.run(LocalSchedulerAgentApplication.class, args);
        System.out.println("✅ Local Scheduler Agent started successfully!");
    }

}
