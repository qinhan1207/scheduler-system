package com.qinhan.controller;

import com.qinhan.client.GlobalSchedulerClient;
import com.qinhan.model.SchedulingEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Arrays;
import java.util.Date;

// 在Bridge项目中创建一个测试Controller
@RestController
@RequestMapping("/api/test")
public class TestController {
    
    @Autowired
    private GlobalSchedulerClient globalSchedulerClient;
    
    @PostMapping("/send-mock-event")
    public String sendMockEvent() {
        SchedulingEvent mockEvent = new SchedulingEvent();
        mockEvent.setName("test-binding");
        mockEvent.setNamespace("default");
        mockEvent.setEventType("ADDED");
        mockEvent.setTimestamp(new Date());
        mockEvent.setWorkloadKind("Deployment");
        mockEvent.setWorkloadName("test-deployment");
        mockEvent.setWorkloadApiVersion("apps/v1");
        mockEvent.setTargetClusters(Arrays.asList("kwok-cluster01", "kwok-cluster02"));
        mockEvent.setScheduled(true);
        mockEvent.setFullyApplied(true);
        
        globalSchedulerClient.sendSchedulingEvent(mockEvent);
        return "✅ 测试事件已发送";
    }
}