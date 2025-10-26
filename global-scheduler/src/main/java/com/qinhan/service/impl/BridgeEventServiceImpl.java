package com.qinhan.service.impl;

import com.qinhan.decision.GlobalDecisionEngine;
import com.qinhan.model.ScheduleDecision;
import com.qinhan.model.SchedulingEvent;
import com.qinhan.model.TentativeRecord;
import com.qinhan.service.BindingUpdaterService;
import com.qinhan.service.BridgeEventService;
import com.qinhan.service.TentativeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

@Slf4j
@Service
public class BridgeEventServiceImpl implements BridgeEventService {

    @Autowired
    private GlobalDecisionEngine globalDecisionEngine;

    @Autowired
    private BindingUpdaterService bindingUpdaterService;

    private final Map<String, SchedulingEvent> eventStore = new ConcurrentHashMap<>();
    private final TentativeService tentativeService;

    public BridgeEventServiceImpl(TentativeService tentativeService) {
        this.tentativeService = tentativeService;
    }

    /**
     * 处理Bridge上报的ResourceBinding事件
     */
    @Override
    public void processBridgeEvent(SchedulingEvent event) {
        try {
            // 存储事件
            String eventKey = event.getNamespace() + "/" + event.getName();
            eventStore.put(eventKey, event);

            log.info("📥 存储Bridge事件: {} - {}/{} - 集群: {}",
                    event.getEventType(), event.getNamespace(), event.getName(),
                    event.getTargetClusters());

            // 判断Bridge事件：如果是ADDED事件，GS评估后创建tentative记录
            if ("ADDED".equals(event.getEventType())) {
                // 1. GS评估调度建议
                ScheduleDecision decision = globalDecisionEngine.evaluateSchedulingEvent(event);
                if (decision.isNeedReschedule()) {
                    // 2. 创建TentativeRecord
                    TentativeRecord tentativeRecord = TentativeRecord.builder()
                            .workloadName(decision.getWorkloadName())
                            .namespace(decision.getNamespace())
                            .recommendedCluster(decision.getRecommendedCluster())
                            .score(decision.getRecommendedScore())
                            .reason(decision.getReason())
                            .build();
                    // 3. 调用TentativeService创建调度建议
                    String recordId = tentativeService.createSchedulingProposal(tentativeRecord);
                    log.info("🔄 创建tentative调度建议: ID={}, {} -> {}",
                            recordId, decision.getWorkloadName(), decision.getRecommendedCluster());
                } else {
                    log.info("✅ 与Karmada调度一致，跳过tentative: {}", decision.getReason());
                }
            }

            // 可以在这里添加更多业务逻辑，比如：
            // - 分析调度决策是否合理
            // - 记录调度历史用于优化
            // - 触发重新调度评估


        } catch (Exception e) {
            log.error("❌ 处理Bridge事件失败: {}/{}", event.getNamespace(), event.getName(), e);
        }
    }

    /**
     * 获取所有Bridge事件记录
     */
    @Override
    public List<SchedulingEvent> getAllBridgeEvents() {
        return new ArrayList<>(eventStore.values());
    }

    /**
     * 根据工作负载名称查找事件
     */
    @Override
    public List<SchedulingEvent> getEventsByWorkload(String workloadName) {
        return eventStore.values().stream()
                .filter(event -> workloadName.equals(event.getWorkloadName()))
                .collect(Collectors.toList());
    }
}