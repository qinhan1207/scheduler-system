package com.qinhan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class LocalSchedulerAgentApplication {

    public static void main(String[] args) {
        SpringApplication.run(LocalSchedulerAgentApplication.class, args);
        System.out.println("✅ Local Scheduler Agent started successfully!");
    }

}
