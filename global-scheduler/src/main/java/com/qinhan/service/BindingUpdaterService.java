package com.qinhan.service;

import com.qinhan.model.ScheduleDecision;

/**
 * BindingUpdaterService 定义更新 ResourceBinding 的标准接口。
 * 该接口负责根据全局调度决策结果更新 Karmada 的绑定对象。
 */
public interface BindingUpdaterService {

    /**
     * 根据调度决策结果更新 ResourceBinding。
     * @param decision 全局调度决策结果
     */
    void updateBinding(ScheduleDecision decision);
}
