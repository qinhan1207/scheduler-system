package com.qinhan.service;

import com.qinhan.model.TentativeRecord;

import java.util.List;

/**
 * Tentative 调度协调服务
 * 负责管理暂定的调度建议，协调确认或拒绝
 */
public interface TentativeService {
    
    /**
     * 创建新的调度建议
     */
    String createSchedulingProposal(TentativeRecord record);
    
    /**
     * 处理待定的调度建议（确认/拒绝）
     */
    void processPendingProposals();
    
    /**
     * 手动确认调度建议
     */
    boolean confirmProposal(String recordId, String reason);
    
    /**
     * 手动拒绝调度建议
     */
    boolean rejectProposal(String recordId, String reason);
    
    /**
     * 获取所有调度建议记录
     */
    List<TentativeRecord> getAllRecords();
    
    /**
     * 根据状态获取记录
     */
    List<TentativeRecord> getRecordsByState(String state);
    
    /**
     * 获取特定工作负载的记录
     */
    List<TentativeRecord> getRecordsByWorkload(String workloadName);
}