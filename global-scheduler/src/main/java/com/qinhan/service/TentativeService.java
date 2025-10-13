package com.qinhan.service;


import com.qinhan.model.TentativeRecord;

import java.util.List;

public interface TentativeService {
    // 上报一个tentative调度建议
    void addTentative(TentativeRecord record);

    // 查询所有记录
    List<TentativeRecord> getAllRecords();

    void processTentatives(); // 定期处理 tentative 记录（确认/拒绝）

}
