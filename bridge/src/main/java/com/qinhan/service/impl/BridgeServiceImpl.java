package com.qinhan.service.impl;

import com.qinhan.service.BridgeService;
import com.qinhan.util.K8sClientUtil;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.Configuration;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import io.kubernetes.client.openapi.Pair;
import io.kubernetes.client.util.Watch;
import lombok.extern.slf4j.Slf4j;
import okhttp3.Call;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import com.google.gson.reflect.TypeToken;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;

/**
 * BridgeServiceImpl
 * -------------------------------------------------
 * Karmada Bridge 服务实现
 * 监听 Karmada Work 对象并协调全局调度决策
 */
@Slf4j
@Service
public class BridgeServiceImpl implements BridgeService {

    @Value("${kubeconfig.path}")
    private String kubeConfigPath;

    @Value("${global.scheduler.url:http://localhost:8080}")
    private String globalSchedulerUrl;

    @Value("${bridge.watch.enabled:true}")
    private boolean watchEnabled;

    @Value("${bridge.watch.retry.interval:5000}")
    private long retryInterval;

    private final RestTemplate restTemplate = new RestTemplate();
    private volatile boolean watching = false;
    private ScheduledExecutorService watchExecutor;
    private ScheduledExecutorService healthCheckExecutor;

    // 统计信息
    private final AtomicLong eventCount = new AtomicLong(0);
    private final AtomicLong lastEventTime = new AtomicLong(0);
    private final List<String> recentActivities = Collections.synchronizedList(new ArrayList<>());
    private final Map<String, WorkInfo> workCache = new ConcurrentHashMap<>();

    // Work 信息类
    private static class WorkInfo {
        String name;
        String namespace;
        String status;
        long createTime;
        long updateTime;
        String scheduledCluster;

        WorkInfo(String name, String namespace) {
            this.name = name;
            this.namespace = namespace;
            this.createTime = System.currentTimeMillis();
            this.updateTime = this.createTime;
        }
    }

    @PostConstruct
    public void init() {
        log.info("🔧 初始化 Karmada Bridge 服务");

        if (watchEnabled) {
            startWatch();
        } else {
            log.info("⏸️ Watch 功能已禁用，如需启用请设置 bridge.watch.enabled=true");
        }

        // 启动健康检查
        startHealthCheck();
    }

    @PreDestroy
    public void destroy() {
        log.info("🛑 正在关闭 Karmada Bridge 服务...");
        stopWatch();

        if (healthCheckExecutor != null) {
            healthCheckExecutor.shutdown();
        }

        log.info("✅ Karmada Bridge 服务已关闭");
    }

    @Override
    public void startWatch() {
        if (watching) {
            log.warn("⚠️ Watch 已经在运行中");
            return;
        }

        log.info("🚀 启动 Karmada Work 监听器");
        watching = true;

        watchExecutor = Executors.newSingleThreadScheduledExecutor();
        watchExecutor.execute(this::watchLoop);

        addActivity("监听器启动成功");
    }

    @Override
    public void stopWatch() {
        log.info("🛑 停止 Karmada Work 监听器");
        watching = false;

        if (watchExecutor != null) {
            watchExecutor.shutdown();
            try {
                if (!watchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                    watchExecutor.shutdownNow();
                }
            } catch (InterruptedException e) {
                watchExecutor.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }

        addActivity("监听器已停止");
    }

    @Override
    public boolean isWatcherRunning() {
        return watching;
    }

    @Override
    public String getWatcherStatus() {
        if (!watching) {
            return "STOPPED";
        }

        long lastEvent = lastEventTime.get();
        if (lastEvent > 0 && System.currentTimeMillis() - lastEvent > 60000) {
            return "RUNNING_NO_EVENTS";
        }

        return "RUNNING";
    }

    @Override
    public String getRecentActivity() {
        synchronized (recentActivities) {
            if (recentActivities.isEmpty()) {
                return "暂无活动";
            }
            return String.join("\n", recentActivities);
        }
    }

    @Override
    public boolean triggerScheduling(String workName, String namespace) {
        try {
            log.info("🎯 手动触发调度检查 - Work: {}/{}", namespace, workName);

            WorkInfo workInfo = workCache.get(getWorkKey(namespace, workName));
            if (workInfo == null) {
                log.warn("⚠️ 未找到 Work: {}/{}", namespace, workName);
                return false;
            }

            Map<String, Object> request = new HashMap<>();
            request.put("eventType", "MANUAL");
            request.put("workName", workName);
            request.put("namespace", namespace);
            request.put("timestamp", System.currentTimeMillis());

            Map response = restTemplate.postForObject(
                    globalSchedulerUrl + "/api/schedule/work",
                    request,
                    Map.class
            );

            boolean success = response != null && response.containsKey("cluster");
            if (success) {
                workInfo.scheduledCluster = (String) response.get("cluster");
                addActivity("手动调度成功 - Work: " + workName + ", Cluster: " + workInfo.scheduledCluster);
            } else {
                addActivity("手动调度失败 - Work: " + workName);
            }

            return success;

        } catch (Exception e) {
            log.error("❌ 手动触发调度失败 - Work: {}/{}: {}", namespace, workName, e.getMessage());
            addActivity("手动调度异常 - Work: " + workName + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * 主要的 Watch 循环 - 使用正确的 buildCall 签名
     */
    private void watchLoop() {
        while (watching) {
            Watch<Object> watch = null;
            try {
                log.info("📡 连接 Karmada API Server...");

                ApiClient client = K8sClientUtil.createApiClient(kubeConfigPath);
                Configuration.setDefaultApiClient(client);

                // 使用正确的 buildCall 签名
                String baseUrl = client.getBasePath(); // 获取基础 URL
                String path = "/apis/work.karmada.io/v1alpha2/works";

                // 构建查询参数
                List<io.kubernetes.client.openapi.Pair> queryParams = new ArrayList<>();
                queryParams.add(new io.kubernetes.client.openapi.Pair("watch", "true"));

                // 构建 Call 对象 - 使用正确的 11 个参数
                Call call = client.buildCall(
                        baseUrl,           // baseUrl - 第一个参数
                        path,              // path - 第二个参数
                        "GET",             // method - 第三个参数
                        queryParams,       // queryParams - 第四个参数
                        new ArrayList<>(), // collectionQueryParams - 第五个参数
                        null,              // body - 第六个参数
                        new HashMap<>(),   // headerParams - 第七个参数
                        new HashMap<>(),   // cookieParams - 第八个参数
                        new HashMap<>(),   // formParams - 第九个参数
                        new String[]{"BearerToken"}, // authNames - 第十个参数
                        null               // callback - 第十一个参数
                );

                // 创建 Watch
                watch = Watch.createWatch(
                        client,
                        call,
                        new TypeToken<Watch.Response<Object>>() {}.getType()
                );

                log.info("✅ 成功连接到 Karmada API Server，开始监听 Work 事件");
                addActivity("成功连接到 Karmada API Server");

                // 处理事件流
                for (Watch.Response<Object> event : watch) {
                    if (!watching) {
                        break;
                    }
                    processEvent(event);
                }

            } catch (Exception e) {
                log.error("❌ Watch 连接异常: {}", e.getMessage(), e);

                if (watching) {
                    log.info("🔄 {}ms 后尝试重新连接...", retryInterval);
                    try {
                        Thread.sleep(retryInterval);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            } finally {
                // 确保关闭 watch
                if (watch != null) {
                    try {
                        watch.close();
                    } catch (Exception e) {
                        log.warn("⚠️ 关闭 Watch 时发生错误: {}", e.getMessage());
                    }
                }
            }
        }
        log.info("🛑 Watch 监听循环结束");
    }

    /**
     * 处理 Watch 事件
     */
    @SuppressWarnings("unchecked")
    private void processEvent(Watch.Response<Object> event) {
        eventCount.incrementAndGet();
        lastEventTime.set(System.currentTimeMillis());

        try {
            String eventType = event.type;
            Object object = event.object;

            if (!(object instanceof Map)) {
                log.warn("⚠️ 接收到非 Map 类型的事件对象");
                return;
            }

            Map<String, Object> work = (Map<String, Object>) object;
            Map<String, Object> metadata = (Map<String, Object>) work.get("metadata");

            if (metadata == null) {
                log.warn("⚠️ Work 对象缺少 metadata");
                return;
            }

            String name = (String) metadata.get("name");
            String namespace = (String) metadata.get("namespace");

            if (name == null || namespace == null) {
                log.warn("⚠️ Work 对象缺少 name 或 namespace");
                return;
            }

            String workKey = getWorkKey(namespace, name);

            log.info("🔔 处理 Work 事件 - 类型: {}, Work: {}/{}", eventType, namespace, name);

            switch (eventType) {
                case "ADDED":
                    handleWorkAdded(workKey, name, namespace, work);
                    break;
                case "MODIFIED":
                    handleWorkModified(workKey, name, namespace, work);
                    break;
                case "DELETED":
                    handleWorkDeleted(workKey, name, namespace);
                    break;
                default:
                    log.debug("📨 未知事件类型: {}", eventType);
                    break;
            }

        } catch (Exception e) {
            log.error("❌ 处理事件时发生错误: {}", e.getMessage(), e);
            addActivity("处理事件异常: " + e.getMessage());
        }
    }

    private void handleWorkAdded(String workKey, String name, String namespace, Map<String, Object> work) {
        WorkInfo workInfo = new WorkInfo(name, namespace);
        workInfo.status = "Pending";
        workCache.put(workKey, workInfo);

        log.info("🆕 新增 Work: {}/{}", namespace, name);
        addActivity("新增 Work: " + name + " (" + namespace + ")");

        // 调用全局调度器
        callGlobalScheduler("ADDED", name, namespace, work);
    }

    private void handleWorkModified(String workKey, String name, String namespace, Map<String, Object> work) {
        WorkInfo workInfo = workCache.get(workKey);
        if (workInfo != null) {
            workInfo.updateTime = System.currentTimeMillis();

            // 更新状态
            Map<String, Object> status = (Map<String, Object>) work.get("status");
            if (status != null) {
                workInfo.status = (String) status.get("phase");
                log.info("📊 Work 状态更新: {}/{} -> {}", namespace, name, workInfo.status);
            }
        } else {
            // 如果缓存中没有，当作新增处理
            handleWorkAdded(workKey, name, namespace, work);
            return;
        }

        log.info("🔁 更新 Work: {}/{}", namespace, name);
        addActivity("更新 Work: " + name + " (" + namespace + ")");

        callGlobalScheduler("MODIFIED", name, namespace, work);
    }

    private void handleWorkDeleted(String workKey, String name, String namespace) {
        workCache.remove(workKey);

        log.info("🗑️ 删除 Work: {}/{}", namespace, name);
        addActivity("删除 Work: " + name + " (" + namespace + ")");

        callGlobalScheduler("DELETED", name, namespace, null);
    }

    private void callGlobalScheduler(String eventType, String workName, String namespace, Map<String, Object> work) {
        try {
            Map<String, Object> request = new HashMap<>();
            request.put("eventType", eventType);
            request.put("workName", workName);
            request.put("namespace", namespace);
            request.put("timestamp", System.currentTimeMillis());

            log.info("📞 调用全局调度器 - Work: {}/{}, 事件: {}", namespace, workName, eventType);

            Map response = restTemplate.postForObject(
                    globalSchedulerUrl + "/api/schedule/work",
                    request,
                    Map.class
            );

            if (response != null) {
                log.info("✅ 调度器响应 - Work: {}/{}: {}", namespace, workName, response);
                if (response.containsKey("cluster")) {
                    reportTentativeSchedule(workName, namespace, response);
                }
            } else {
                log.warn("⚠️ 调度器返回空响应 - Work: {}/{}", namespace, workName);
            }

        } catch (Exception e) {
            log.error("❌ 调用全局调度器失败 - Work: {}/{}: {}", namespace, workName, e.getMessage());
            addActivity("调度调用失败: " + workName + " - " + e.getMessage());
        }
    }

    private void reportTentativeSchedule(String workName, String namespace, Map<String, Object> response) {
        try {
            Map<String, Object> tentative = new HashMap<>();
            tentative.put("workName", workName);
            tentative.put("namespace", namespace);
            tentative.put("cluster", response.get("cluster"));
            tentative.put("score", response.get("score"));
            tentative.put("reason", response.get("reason"));
            tentative.put("timestamp", System.currentTimeMillis());

            String result = restTemplate.postForObject(
                    globalSchedulerUrl + "/api/tentative/work",
                    tentative,
                    String.class
            );

            log.info("✅ 临时调度结果上报 - Work: {}/{}: {}", namespace, workName, result);
            addActivity("调度结果上报: " + workName + " -> " + response.get("cluster"));

            // 更新缓存中的调度信息
            String workKey = getWorkKey(namespace, workName);
            WorkInfo workInfo = workCache.get(workKey);
            if (workInfo != null) {
                workInfo.scheduledCluster = (String) response.get("cluster");
            }

        } catch (Exception e) {
            log.error("❌ 上报临时调度结果失败 - Work: {}/{}: {}", namespace, workName, e.getMessage());
        }
    }

    private void startHealthCheck() {
        healthCheckExecutor = Executors.newSingleThreadScheduledExecutor();
        healthCheckExecutor.scheduleAtFixedRate(() -> {
            if (watching) {
                long now = System.currentTimeMillis();
                long lastEvent = lastEventTime.get();

                if (lastEvent > 0 && now - lastEvent > 120000) {
                    log.warn("⚠️ 健康检查: 长时间未接收到事件");
                } else {
                    log.debug("🐶 健康检查: 运行正常, 事件总数: {}", eventCount.get());
                }
            }
        }, 30, 60, TimeUnit.SECONDS);
    }

    private void addActivity(String activity) {
        synchronized (recentActivities) {
            String timestamp = new Date().toString();
            recentActivities.add(0, "[" + timestamp + "] " + activity);

            // 保持最近50条活动记录
            if (recentActivities.size() > 50) {
                recentActivities.remove(recentActivities.size() - 1);
            }
        }
    }

    private String getWorkKey(String namespace, String name) {
        return namespace + "/" + name;
    }

    /**
     * 获取统计信息（用于监控）
     */
    public Map<String, Object> getStatistics() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("watching", watching);
        stats.put("eventCount", eventCount.get());
        stats.put("lastEventTime", lastEventTime.get() > 0 ? new Date(lastEventTime.get()) : "N/A");
        stats.put("cachedWorks", workCache.size());
        stats.put("recentActivitiesCount", recentActivities.size());
        return stats;
    }

    /**
     * 获取缓存的 Work 列表
     */
    public List<Map<String, Object>> getCachedWorks() {
        List<Map<String, Object>> works = new ArrayList<>();
        for (WorkInfo workInfo : workCache.values()) {
            Map<String, Object> info = new HashMap<>();
            info.put("name", workInfo.name);
            info.put("namespace", workInfo.namespace);
            info.put("status", workInfo.status);
            info.put("scheduledCluster", workInfo.scheduledCluster);
            info.put("createTime", new Date(workInfo.createTime));
            info.put("updateTime", new Date(workInfo.updateTime));
            works.add(info);
        }
        works.sort((a, b) -> ((Date) b.get("createTime")).compareTo((Date) a.get("createTime")));
        return works;
    }




}