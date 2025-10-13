package com.qinhan;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class GlobalSchedulerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GlobalSchedulerApplication.class, args);
        System.out.println("✅ Global Scheduler started successfully!");
    }

}
