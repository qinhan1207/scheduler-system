package com.qinhan.controller;


import com.qinhan.model.SchedulingRequest;
import com.qinhan.model.SchedulingResponse;
import com.qinhan.service.SchedulingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequestMapping("/api")
public class SchedulingController {

    @Autowired
    private SchedulingService schedulingService;

    /**
     * 调度决策接口
     * @param request 请求的参数
     * @return 返回推荐的集群
     */
    @PostMapping("/schedule")
    public SchedulingResponse schedule(@RequestBody(required = false)SchedulingRequest request){
        log.info("调度决策:{}",request);
        if (request==null){
            request = new SchedulingRequest(); // 空请求允许（以全局视角选）
        }
        SchedulingResponse resp = schedulingService.selectBestCluster(request);
        return resp;
    }
}
