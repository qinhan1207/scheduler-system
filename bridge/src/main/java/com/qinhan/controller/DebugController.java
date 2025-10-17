package com.qinhan.controller;

import com.qinhan.service.BridgeService;
import com.qinhan.util.K8sClientUtil;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    private static final String GROUP = "work.karmada.io";
    private static final String VERSION = "v1alpha2";
    private static final String PLURAL = "resourcebindings";


    @Autowired
    private K8sClientUtil k8sClientUtil;

    @Autowired
    private BridgeService bridgeService;
    
    @GetMapping("/test-connection")
    public String testConnection() {
        boolean success = bridgeService.testConnection();
        return success ? "✅ 连接成功" : "❌ 连接失败";
    }
    
    @GetMapping("/list-bindings")
    public String listBindings() {
        try {
            ApiClient client = k8sClientUtil.getClient("E:\\karmada-config");
            CustomObjectsApi api = new CustomObjectsApi(client);
            
            Object result = api.listClusterCustomObject(GROUP, VERSION, PLURAL)
                    .execute();
            
            return "✅ 当前ResourceBindings: " + result.toString();
        } catch (Exception e) {
            return "❌ 获取ResourceBindings失败: " + e.getMessage();
        }
    }
}