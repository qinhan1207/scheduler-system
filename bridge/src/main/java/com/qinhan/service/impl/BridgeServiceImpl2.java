//package com.qinhan.service.impl;
//
//import com.qinhan.service.BridgeService;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.stereotype.Service;
//
//import javax.annotation.PostConstruct;
//import javax.annotation.PreDestroy;
//import java.io.BufferedReader;
//import java.io.InputStreamReader;
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.List;
//import java.util.concurrent.Executors;
//import java.util.concurrent.ScheduledExecutorService;
//import java.util.concurrent.TimeUnit;
//
//@Slf4j
//@Service
//public class BridgeServiceImpl2
//
//        implements BridgeService {
//
//    @Value("${kubeconfig.path:E:/karmada-config}")
//    private String kubeConfigPath;
//
//    // 使用你之前下载的 kubectl 完整路径
//    private final String kubectlPath = "D:\\develop\\kubectl\\kubectl.exe";
//
//    private volatile boolean watching = false;
//    private ScheduledExecutorService watchExecutor;
//    private Process watchProcess;
//
//    // 活动记录
//    private final List<String> recentActivities = Collections.synchronizedList(new ArrayList<>());
//
//    @PostConstruct
//    public void init() {
//        log.info("🔧 初始化 Karmada Bridge 服务");
//        startWatch();
//    }
//
//    @PreDestroy
//    public void destroy() {
//        log.info("🛑 正在关闭 Karmada Bridge 服务...");
//        stopWatch();
//        log.info("✅ Karmada Bridge 服务已关闭");
//    }
//
//    @Override
//    public void startWatch() {
//        if (watching) {
//            log.warn("⚠️ Watch 已经在运行中");
//            return;
//        }
//
//        log.info("🚀 启动 Karmada Work 监听器");
//        watching = true;
//
//        watchExecutor = Executors.newSingleThreadScheduledExecutor();
//        watchExecutor.execute(this::watchLoop);
//
//        addActivity("监听器启动成功");
//    }
//
//    @Override
//    public void stopWatch() {
//        log.info("🛑 停止 Karmada Work 监听器");
//        watching = false;
//
//        if (watchProcess != null) {
//            watchProcess.destroy();
//            watchProcess = null;
//        }
//
//        if (watchExecutor != null) {
//            watchExecutor.shutdown();
//            try {
//                if (!watchExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
//                    watchExecutor.shutdownNow();
//                }
//            } catch (InterruptedException e) {
//                watchExecutor.shutdownNow();
//                Thread.currentThread().interrupt();
//            }
//        }
//
//        addActivity("监听器已停止");
//    }
//
//    @Override
//    public boolean isWatcherRunning() {
//        return watching;
//    }
//
//    @Override
//    public String getWatcherStatus() {
//        return watching ? "RUNNING" : "STOPPED";
//    }
//
//    @Override
//    public String getRecentActivity() {
//        synchronized (recentActivities) {
//            if (recentActivities.isEmpty()) {
//                return "暂无活动";
//            }
//            return String.join("\n", recentActivities);
//        }
//    }
//
//    @Override
//    public boolean triggerScheduling(String workName, String namespace) {
//        log.info("手动调度功能暂未实现");
//        return false;
//    }
//
//    /**
//     * 使用 kubectl 命令行创建 Watch - 指定完整路径
//     */
//    private void watchLoop() {
//        while (watching) {
//            try {
//                log.info("📡 使用 kubectl 连接 Karmada API Server...");
//                log.info("🔧 kubectl 路径: {}", kubectlPath);
//                log.info("📁 kubeconfig 路径: {}", kubeConfigPath);
//
//                // 构建 kubectl 命令
//                ProcessBuilder pb = new ProcessBuilder(
//                        kubectlPath,
//                        "--kubeconfig", kubeConfigPath,
//                        "--insecure-skip-tls-verify=true",
//                        "get", "works.work.karmada.io",
//                        "--watch",
//                        "-o", "json"
//                );
//
//                // 启动进程
//                watchProcess = pb.start();
//
//                // 读取输出流
//                BufferedReader reader = new BufferedReader(
//                        new InputStreamReader(watchProcess.getInputStream())
//                );
//
//                // 读取错误流（用于调试）
//                BufferedReader errorReader = new BufferedReader(
//                        new InputStreamReader(watchProcess.getErrorStream())
//                );
//
//                String line;
//                log.info("✅ 成功连接到 Karmada API Server，开始监听 Work 事件");
//                addActivity("成功连接到 Karmada API Server");
//
//                // 启动错误流读取线程
//                Thread errorThread = new Thread(() -> {
//                    try {
//                        String errorLine;
//                        while ((errorLine = errorReader.readLine()) != null && watching) {
//                            if (!errorLine.trim().isEmpty()) {
//                                log.warn("⚠️ kubectl 错误输出: {}", errorLine);
//                            }
//                        }
//                    } catch (Exception e) {
//                        log.warn("⚠️ 读取错误流异常: {}", e.getMessage());
//                    }
//                });
//                errorThread.setDaemon(true);
//                errorThread.start();
//
//                // 读取正常输出
//                while ((line = reader.readLine()) != null && watching) {
//                    if (!line.trim().isEmpty()) {
//                        log.info("📨 收到 Work 事件: {}", line);
//                        processWorkEvent(line);
//                    }
//                }
//
//                // 如果走到这里，说明连接断开
//                if (watching) {
//                    log.warn("⚠️ Watch 连接断开，等待重连...");
//                    addActivity("Watch 连接断开，等待重连");
//                    Thread.sleep(5000);
//                }
//
//            } catch (Exception e) {
//                log.error("❌ Watch 连接异常: {}", e.getMessage());
//                addActivity("Watch 连接异常: " + e.getMessage());
//
//                if (watching) {
//                    log.info("🔄 5秒后尝试重新连接...");
//                    try {
//                        Thread.sleep(5000);
//                    } catch (InterruptedException ie) {
//                        Thread.currentThread().interrupt();
//                        break;
//                    }
//                }
//            } finally {
//                if (watchProcess != null) {
//                    watchProcess.destroy();
//                    watchProcess = null;
//                }
//            }
//        }
//        log.info("🛑 Watch 监听循环结束");
//    }
//
//    /**
//     * 处理 Work 事件
//     */
//    private void processWorkEvent(String jsonLine) {
//        try {
//            // 简单的日志处理
//            if (jsonLine.contains("\"kind\":\"Work\"")) {
//                log.info("🎯 检测到 Work 资源事件");
//                addActivity("收到 Work 事件");
//            } else if (jsonLine.contains("\"type\":\"ADDED\"")) {
//                log.info("🆕 检测到 ADDED 事件");
//                addActivity("Work 新增事件");
//            } else if (jsonLine.contains("\"type\":\"MODIFIED\"")) {
//                log.info("🔁 检测到 MODIFIED 事件");
//                addActivity("Work 更新事件");
//            } else if (jsonLine.contains("\"type\":\"DELETED\"")) {
//                log.info("🗑️ 检测到 DELETED 事件");
//                addActivity("Work 删除事件");
//            }
//
//        } catch (Exception e) {
//            log.error("❌ 处理事件时发生错误: {}", e.getMessage());
//        }
//    }
//
//    private void addActivity(String activity) {
//        synchronized (recentActivities) {
//            String timestamp = java.time.LocalDateTime.now().toString();
//            recentActivities.add(0, "[" + timestamp + "] " + activity);
//
//            // 保持最近50条活动记录
//            if (recentActivities.size() > 50) {
//                recentActivities.remove(recentActivities.size() - 1);
//            }
//        }
//    }
//}