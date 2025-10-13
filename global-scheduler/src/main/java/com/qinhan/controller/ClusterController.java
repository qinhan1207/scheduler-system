package com.qinhan.controller;


import com.qinhan.model.ClusterStatus;
import com.qinhan.service.ClusterService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Slf4j
@RestController
@RequestMapping("/api/clusters")
public class ClusterController {

    @Autowired
    private ClusterService clusterService;

    /**
     * 接收上报的数据
     * @param status
     * @return
     */
    @PostMapping("/report")
    public String reportStatus(@RequestBody ClusterStatus status) {
        log.info("接收上报的数据:{}",status);
        clusterService.updateClusterStatus(status);
        return "✅ Received status from cluster: " + status.getClusterName();
    }

    // 获取全部集群状态
    @GetMapping("/all")
    public List<ClusterStatus> getAllStatus() {
        log.info("查看所有集群状态");
        return clusterService.getAllClusterStatus();
    }
}
