package com.qinhan.service.impl;

import com.qinhan.service.BridgeService;
import com.qinhan.util.K8sClientUtil;
import io.kubernetes.client.informer.ResourceEventHandler;
import io.kubernetes.client.informer.SharedInformerFactory;
import io.kubernetes.client.informer.SharedIndexInformer;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CoreV1Api;
import io.kubernetes.client.openapi.models.V1Pod;
import io.kubernetes.client.openapi.models.V1PodList;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import javax.annotation.PostConstruct;
import java.util.HashMap;
import java.util.Map;

/**
 * Bridge 核心逻辑：
 * 1. 监听 Karmada 控制面的 Pod 创建事件；
 * 2. 调用 Global Scheduler 的 /api/schedule 获取推荐；
 * 3. 调用 Global Scheduler 的 /api/tentative 上报；
 * 4. （下一阶段）写回 annotation 到 Pod（可扩展）
 */
@Slf4j
@Service
public class BridgeServiceImpl implements BridgeService {

    @Value("${kubeconfig.path}")
    private String kubeconfigPath;

    @Value("${global.scheduler.url}")
    private String globalSchedulerUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostConstruct
    public void startPodWatcher() {
        new Thread(() -> {
            try {
                log.info("Starting Pod watcher using kubeconfig: {}", kubeconfigPath);
                // 初始化客户端
                ApiClient client = K8sClientUtil.createApiClient(kubeconfigPath);
                Configuration.setDefaultApiClient(client);

                CoreV1Api api = new CoreV1Api();
                SharedInformerFactory factory = new SharedInformerFactory();

                // 创建pod监听器
                SharedIndexInformer<V1Pod> podInformer = factory.sharedIndexInformerFor(
                        (callGeneratorParams) -> api.listPodForAllNamespacesCall(
                                null, null, null, null, null, null,
                                callGeneratorParams.resourceVersion,
                                null, callGeneratorParams.timeoutSeconds, true, null),
                        V1Pod.class, V1PodList.class, 0);

                podInformer.addEventHandler(
                        new ResourceEventHandler<>() {
                            @Override
                            public void onAdd(V1Pod pod) {
                                if (pod.getMetadata() == null) return;
                                String podName = pod.getMetadata().getName();
                                String ns = pod.getMetadata().getNamespace();
                                log.info("Detected new Pod: {}/{}", ns, podName);

                                // Step 1: 调用 /api/schedule 获取推荐集群
                                try {
                                    Map<String, Object> req = new HashMap<>();
                                    req.put("podName", podName);
                                    req.put("namespace", ns);
                                    Map resp = restTemplate.postForObject(globalSchedulerUrl + "/api/schedule", req, Map.class);
                                    log.info("Scheduling recommendation: {}", resp);

                                    // Step 2: 上报 tentative
                                    Map<String, Object> tentative = new HashMap<>();
                                    tentative.put("podName", podName);
                                    tentative.put("namespace", ns);
                                    tentative.put("cluster", resp.get("cluster"));
                                    tentative.put("score", resp.get("score"));
                                    tentative.put("reason", resp.get("reason"));
                                    restTemplate.postForObject(globalSchedulerUrl + "/api/tentative", tentative, String.class);

                                } catch (Exception e) {
                                    log.error("Error calling Global Scheduler", e);
                                }
                            }

                            @Override
                            public void onUpdate(V1Pod oldPod, V1Pod newPod) {
                            }

                            @Override
                            public void onDelete(V1Pod pod, boolean deletedFinalStateUnknown) {
                            }
                        });

                    factory.startAllRegisteredInformers();
            } catch (Exception e) {
                log.error("Failed to start Pod watcher", e);
            }
        }).start();
    }
}
