package com.qinhan.controller;

import com.qinhan.model.TentativeRecord;
import com.qinhan.service.TentativeService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用于接收 Bridge上报的tentative调度请求
 */
@Slf4j
@RestController
@RequestMapping("/api/tentative")
public class TentativeController {

    @Autowired
    private TentativeService tentativeService;

    /**
     * 上报一个tentative调度建议
     * @param record
     * @return
     */
    @PostMapping
    public String addTentative(@RequestBody TentativeRecord record) {
        log.info("上报一个tentative:{}",record);
        tentativeService.addTentative(record);
        return "Tentative record accepted for pod:" + record.getPodName();
    }

    /**
     * 查询所有记录
     * @return
     */
    @GetMapping("/all")
    public List<TentativeRecord> getAll(){
        log.info("查询所有tentative记录");
        return tentativeService.getAllRecords();
    }

}
