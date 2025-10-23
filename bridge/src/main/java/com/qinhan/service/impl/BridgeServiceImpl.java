package com.qinhan.service.impl;

import com.google.gson.reflect.TypeToken;
import com.qinhan.client.GlobalSchedulerClient;
import com.qinhan.model.SchedulingEvent;
import com.qinhan.service.BridgeService;
import com.qinhan.util.K8sClientUtil;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.util.Watch;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
public class BridgeServiceImpl implements BridgeService {

    @Autowired
    private K8sClientUtil k8sClientUtil;

    @Value("${bridge.kubeconfig.path}")
    private String kubeconfigPath;

    @Autowired
    private GlobalSchedulerClient globalSchedulerClient;

    private volatile boolean isInitialSync = true;

    // 上次集群的信息
    private final Map<String, List<String>> lastClusterCache = new ConcurrentHashMap<>();

    private volatile boolean watching = false;
    private Call watchCall;

    private static final String GROUP = "work.karmada.io";
    private static final String VERSION = "v1alpha2";
    private static final String PLURAL = "resourcebindings";

    @Override
    @PostConstruct
    public void startWatch() {
        log.info("🚀 启动ResourceBinding监听器...");
        // 🚨 设置初始同步标志
        isInitialSync = true;
        new Thread(() -> {
            try {
                // 等待一段时间后清除初始同步标志
                Thread.sleep(10000); // 10秒后
                isInitialSync = false;
                log.info("✅ 初始同步完成，开始处理新事件");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }).start();
        new Thread(this::doWatch).start();
    }

    @Override
    public void stopWatch() {
        log.info("🛑 停止ResourceBinding监听...");
        watching = false;
        if (watchCall != null) {
            watchCall.cancel();
        }
    }

    @Override
    public boolean testConnection() {
        try {
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CustomObjectsApi api = new CustomObjectsApi(client);

            // 新的调用方式 - 使用Request Builder
            Object result = api.listClusterCustomObject(GROUP, VERSION, PLURAL)
                    .execute();

            log.info("✅ Karmada连接测试成功");
            return true;
        } catch (ApiException e) {
            log.error("❌ Karmada连接测试失败", e);
            return false;
        }
    }

    /**
     * 核心监听逻辑 - 使用正确的Watch API
     */
    private void doWatch() {

        // 添加启动延迟，避免立即处理历史事件
        try {
            log.info("⏳ Bridge启动延迟10秒，等待系统稳定...");
            Thread.sleep(10000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
        }
        watching = true;

        try {
            // 初始化客户端
            ApiClient client = k8sClientUtil.getClient(kubeconfigPath);
            CustomObjectsApi api = new CustomObjectsApi(client);

            log.info("🔍 开始监听ResourceBinding资源变化...");

            while (watching) {
                try {
                    log.info("🔄 创建新的Watch连接...");
                    // 创建一个监听k8s资源变化的Watch对象
                    Watch<Object> watch = createWatch(api);
                    log.info("✅ Watch连接创建成功，开始监听事件...");

                    int eventCount = 0;

                    // 这里替换为新的Watch循环逻辑
                    for (Watch.Response<Object> response : watch) {
                        if (!watching) {
                            log.info("🛑 监听器已停止，退出循环");
                            break;
                        }

                        eventCount++;

                        // 基于源码的正确处理
                        if (response.object != null && response.object instanceof Map) {
                            Map<String, Object> rbObject = (Map<String, Object>) response.object;

                            log.info("📡 收到事件类型: {}, 开始处理ResourceBinding", response.type);

                            // 直接使用response.type和response.object
                            handleResourceBindingEvent(rbObject, response.type);

                        } else if (response.status != null) {
                            log.info("📡 收到状态事件: {}", response.status);
                        } else {
                            log.warn("⚠️ 收到未知类型的事件: type={}, object={}, status={}",
                                    response.type, response.object, response.status);
                        }

                        // 每处理10个事件记录一次
                        if (eventCount % 10 == 0) {
                            log.info("📊 已处理 {} 个事件", eventCount);
                        }
                    }

                    log.info("🔁 Watch连接断开，准备重连...");

                } catch (ApiException e) {
                    if (watching) {
                        log.error("❌ 监听ResourceBinding发生API异常，5秒后重试...", e);
                        Thread.sleep(5000);
                    }
                } catch (Exception e) {
                    if (watching) {
                        log.error("❌ 监听ResourceBinding发生未知异常，5秒后重试...", e);
                        Thread.sleep(5000);
                    }
                }
            }

        } catch (Exception e) {
            log.error("❌ ResourceBinding监听器发生严重错误", e);
        }
    }

    /**
     * 创建Watch的正确方式 - 使用新的API
     */
    private Watch<Object> createWatch(CustomObjectsApi api) throws ApiException {
        try {
            log.info("🔧 创建Watch连接...");

            // 使用Request Builder创建Call
            Call call = api.listClusterCustomObject(GROUP, VERSION, PLURAL)
                    .pretty(null)
                    .allowWatchBookmarks(null)
                    ._continue(null)
                    .fieldSelector(null)
                    .labelSelector(null)
                    .limit(null)
                    .resourceVersion(null)
                    .resourceVersionMatch(null)
                    .timeoutSeconds(null)
                    .watch(true)  // 关键：开启watch模式
                    .buildCall(null);

            log.info("✅ Watch连接创建成功");

            return Watch.createWatch(
                    api.getApiClient(),
                    call,
                    new TypeToken<Watch.Response<Object>>() {
                    }.getType()
            );
        } catch (Exception e) {
            log.error("❌ 创建Watch失败", e);
            throw e;
        }
    }

    /**
     * 处理ResourceBinding事件 - 正确的方法
     */
    private void handleResourceBindingEvent(Map<String, Object> rbObject, String eventType) {
        try {
            SchedulingEvent event = parseResourceBinding(rbObject, eventType);

//            String resourceKey = event.getNamespace() + "/" + event.getName();
//            // 🚨 精确判断：只有集群配置真正变化时才处理
//            if (shouldSkipEventPrecise(event, rbObject, resourceKey)) {
//                if (log.isTraceEnabled()) { // 🚨 使用TRACE级别避免日志洪水
//                    log.trace("⏭️ 跳过非关键事件: {}/{} - 类型: {}",
//                            event.getNamespace(), event.getName(), event.getEventType());
//                }
//                return;
//            }

            // 🚨 跳过启动时的历史资源同步
            if (isInitialSync && "ADDED".equals(eventType)) {
                log.info("⏭️ 跳过启动同步事件: {}/{}", event.getNamespace(), event.getName());
                return;
            }

            // 🚨 只处理ADDED事件，完全忽略MODIFIED事件
            if (!"ADDED".equals(eventType)) {
                if (log.isTraceEnabled()) {
                    log.trace("⏭️ 跳过MODIFIED事件: {}/{} - 类型: {}",
                            event.getNamespace(), event.getName(), eventType);
                }
                return;
            }

            if (log.isInfoEnabled()) {
                log.info("🎯 ResourceBinding事件: [{}] {}/{} -> clusters={} 状态={}",
                        event.getEventType(),
                        event.getNamespace(),
                        event.getName(),
                        event.getTargetClusters(),
                        event.isScheduled());
            }

            // 发送给GS
            sendToGlobalScheduler(event);
        } catch (Exception e) {
            log.error("❌ 处理ResourceBinding事件失败", e);
        }
    }

    /**
     * 精确判断是否应该跳过事件
     */
    private boolean shouldSkipEventPrecise(SchedulingEvent event, Map<String, Object> rbObject, String resourceKey) {
        // 1. 总是处理ADDED事件（新资源）
        if ("ADDED".equalsIgnoreCase(event.getEventType())) {
            updateClusterCache(resourceKey, event.getTargetClusters());
            return false;
        }

        // 2. 对于MODIFIED事件，只处理集群配置真实变化的情况
        if ("MODIFIED".equalsIgnoreCase(event.getEventType())) {
            return !isRealClusterChange(event, rbObject, resourceKey);
        }

        // 3. 跳过DELETED和其他事件
        return true;
    }

    /**
     * 检查集群配置是否真的发生了变化
     */
    private boolean isRealClusterChange(SchedulingEvent event, Map<String, Object> rbObject, String resourceKey) {
        try {
            // 获取当前的集群配置
            List<String> currentClusters = event.getTargetClusters();
            if (currentClusters == null) {
                return false;
            }

            // 获取上一次的集群配置
            List<String> lastClusters = lastClusterCache.get(resourceKey);

            // 如果是第一次见到该资源，记录并处理
            if (lastClusters == null) {
                updateClusterCache(resourceKey, currentClusters);
                return true;
            }

            // 🚨 精确比较：集群列表是否真的变化
            boolean clustersChanged = !isSameClusterList(lastClusters, currentClusters);

            if (clustersChanged) {
                log.info("🔍 检测到集群配置变化: {} -> {}", lastClusters, currentClusters);
                updateClusterCache(resourceKey, currentClusters);
                return true;
            } else {
                // 集群配置未变化，只是状态更新，跳过
                if (log.isDebugEnabled()) {
                    log.debug("⏭️ 集群配置未变化，跳过状态更新: {}", resourceKey);
                }
                return false;
            }

        } catch (Exception e) {
            log.warn("❌ 集群变化检测失败，保守处理: 跳过事件", e);
            return false;
        }
    }

    /**
     * 比较两个集群列表是否相同（顺序无关）
     */
    private boolean isSameClusterList(List<String> list1, List<String> list2) {
        if (list1 == null && list2 == null) return true;
        if (list1 == null || list2 == null) return false;
        if (list1.size() != list2.size()) return false;

        return new HashSet<>(list1).equals(new HashSet<>(list2));
    }

    /**
     * 更新集群配置缓存
     */
    private void updateClusterCache(String resourceKey, List<String> clusters) {
        if (clusters != null) {
            lastClusterCache.put(resourceKey, new ArrayList<>(clusters));

            // 🚨 限制缓存大小，避免内存泄漏
            if (lastClusterCache.size() > 1000) {
                // 简单的LRU策略：移除最早的一些条目
                Iterator<String> it = lastClusterCache.keySet().iterator();
                int toRemove = lastClusterCache.size() - 800;
                for (int i = 0; i < toRemove && it.hasNext(); i++) {
                    it.next();
                    it.remove();
                }
            }
        }
    }

    /**
     * 解析ResourceBinding为SchedulingEvent
     */
    private SchedulingEvent parseResourceBinding(Map<String, Object> rb, String eventType) {
        Map<String, Object> metadata = (Map<String, Object>) rb.get("metadata");
        Map<String, Object> spec = (Map<String, Object>) rb.get("spec");
        Map<String, Object> status = (Map<String, Object>) rb.get("status");

        SchedulingEvent event = new SchedulingEvent();
        event.setName((String) metadata.get("name"));
        event.setNamespace((String) metadata.get("namespace"));
        event.setEventType(eventType);
        event.setTimestamp(new java.util.Date());

        // 解析资源信息
        if (spec != null) {
            Map<String, Object> resource = (Map<String, Object>) spec.get("resource");
            if (resource != null) {
                event.setWorkloadKind((String) resource.get("kind"));
                event.setWorkloadName((String) resource.get("name"));
                event.setWorkloadApiVersion((String) resource.get("apiVersion"));
            }

            // 解析目标集群
            List<Map<String, Object>> clusters = (List<Map<String, Object>>) spec.get("clusters");
            if (clusters != null) {
                List<String> targetClusters = clusters.stream()
                        .map(cluster -> (String) cluster.get("name"))
                        .toList();
                event.setTargetClusters(targetClusters);
            }
        }

        // 解析状态
        if (status != null) {
            List<Map<String, Object>> conditions = (List<Map<String, Object>>) status.get("conditions");
            if (conditions != null) {
                event.setScheduled(conditions.stream().anyMatch(cond ->
                        "Scheduled".equals(cond.get("type")) && "True".equals(cond.get("status"))));
                event.setFullyApplied(conditions.stream().anyMatch(cond ->
                        "FullyApplied".equals(cond.get("type")) && "True".equals(cond.get("status"))));
            }
        }

        return event;
    }

    /**
     * 发送事件给全局调度器GS
     */
    private void sendToGlobalScheduler(SchedulingEvent event) {
        // TODO: 实现GS的HTTP客户端调用
        globalSchedulerClient.sendSchedulingEvent(event);
        log.info("📤 发送调度事件给GS: {}", event);
    }
}