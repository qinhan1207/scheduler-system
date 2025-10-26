package com.qinhan.controller;

import com.qinhan.model.TentativeRecord;
import com.qinhan.service.TentativeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Tentative 调度协调控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/tentative")
public class TentativeController {
    
    @Autowired
    private TentativeService tentativeService;
    
    /**
     * 创建调度建议
     */
    @PostMapping("/proposals")
    public ResponseEntity<Map<String, String>> createProposal(@RequestBody TentativeRecord record) {
        try {
            String recordId = tentativeService.createSchedulingProposal(record);
            Map<String, String> response = Map.of(
                "recordId", recordId,
                "message", "调度建议创建成功"
            );
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("❌ 创建调度建议失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
    
    /**
     * 手动确认调度建议
     */
    @PostMapping("/proposals/{recordId}/confirm")
    public ResponseEntity<Map<String, String>> confirmProposal(
            @PathVariable String recordId,
            @RequestBody Map<String, String> request) {
        
        String reason = request.getOrDefault("reason", "手动确认");
        boolean success = tentativeService.confirmProposal(recordId, reason);
        
        if (success) {
            return ResponseEntity.ok(Map.of("message", "调度建议确认成功"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "确认调度建议失败"));
        }
    }
    
    /**
     * 手动拒绝调度建议
     */
    @PostMapping("/proposals/{recordId}/reject")
    public ResponseEntity<Map<String, String>> rejectProposal(
            @PathVariable String recordId,
            @RequestBody Map<String, String> request) {
        
        String reason = request.getOrDefault("reason", "手动拒绝");
        boolean success = tentativeService.rejectProposal(recordId, reason);
        
        if (success) {
            return ResponseEntity.ok(Map.of("message", "调度建议拒绝成功"));
        } else {
            return ResponseEntity.badRequest().body(Map.of("error", "拒绝调度建议失败"));
        }
    }
    
    /**
     * 获取所有调度建议记录
     */
    @GetMapping("/records")
    public List<TentativeRecord> getAllRecords() {
        return tentativeService.getAllRecords();
    }
    
    /**
     * 根据状态获取记录
     */
    @GetMapping("/records/state/{state}")
    public List<TentativeRecord> getRecordsByState(@PathVariable String state) {
        return tentativeService.getRecordsByState(state);
    }
    
    /**
     * 获取特定工作负载的记录
     */
    @GetMapping("/records/workload/{workloadName}")
    public List<TentativeRecord> getRecordsByWorkload(@PathVariable String workloadName) {
        return tentativeService.getRecordsByWorkload(workloadName);
    }
    
    /**
     * 立即处理待定建议（手动触发）
     */
    @PostMapping("/process")
    public ResponseEntity<Map<String, String>> processPendingProposals() {
        try {
            tentativeService.processPendingProposals();
            return ResponseEntity.ok(Map.of("message", "待定建议处理完成"));
        } catch (Exception e) {
            log.error("❌ 处理待定建议失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}