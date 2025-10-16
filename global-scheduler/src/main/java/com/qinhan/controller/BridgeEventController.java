package com.qinhan.controller;

import com.qinhan.model.SchedulingEvent;
import com.qinhan.service.BridgeEventService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 专门处理Bridge上报的ResourceBinding事件
 */
@Slf4j
@RestController
@RequestMapping("/api/bridge")
public class BridgeEventController {

    @Autowired
    private BridgeEventService bridgeEventService;

    /**
     * 接收Bridge上报的ResourceBinding事件
     */
    @PostMapping("/events")
    public String handleBridgeEvent(@RequestBody SchedulingEvent event) {
        log.info("📥 收到Bridge ResourceBinding事件: {} - {}/{}", 
                event.getEventType(), event.getNamespace(), event.getName());
        
        bridgeEventService.processBridgeEvent(event);
        
        return "✅ Bridge事件处理完成: " + event.getName();
    }

    /**
     * 查询所有Bridge事件
     */
    @GetMapping("/events")
    public List<SchedulingEvent> getAllBridgeEvents() {
        return bridgeEventService.getAllBridgeEvents();
    }

    /**
     * 根据工作负载名称查询事件
     */
    @GetMapping("/events/workload/{workloadName}")
    public List<SchedulingEvent> getEventsByWorkload(@PathVariable String workloadName) {
        return bridgeEventService.getEventsByWorkload(workloadName);
    }
}