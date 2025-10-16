package com.qinhan;

import com.qinhan.service.BridgeService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@EnableScheduling
@SpringBootApplication
public class BridgeApplication implements CommandLineRunner {

    @Autowired
    private BridgeService bridgeService;

    public static void main(String[] args) {
        SpringApplication.run(BridgeApplication.class, args);
    }

    @Override
    public void run(String... args) {
        bridgeService.startWatch();  // ✅ 这句必须存在，否则监听不会触发
    }
}
