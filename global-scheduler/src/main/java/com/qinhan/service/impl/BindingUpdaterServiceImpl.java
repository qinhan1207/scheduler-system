package com.qinhan.service.impl;

import com.qinhan.model.ScheduleDecision;
import com.qinhan.service.BindingUpdaterService;
import io.kubernetes.client.openapi.ApiClient;
import io.kubernetes.client.openapi.ApiException;
import io.kubernetes.client.openapi.apis.CustomObjectsApi;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class BindingUpdaterServiceImpl implements BindingUpdaterService {

    private final ApiClient karmadaClient;

    public BindingUpdaterServiceImpl(ApiClient karmadaClient) {
        this.karmadaClient = karmadaClient;
    }

    /**
     * 根据全局调度决策结果，更新对应的 ResourceBinding 对象。
     *
     * @param decision 全局调度评估的结果对象
     */
    @Override
    public void updateBinding(ScheduleDecision decision) {
        if (!decision.isNeedReschedule()) {
            log.info("✅ workload [{}] 无需重新调度，跳过更新。", decision.getWorkloadName());
            return;
        }

        String namespace = decision.getNamespace();
        String bindingName = decision.getWorkloadName() + "-deployment"; // 👈 可根据你的实际 RB 命名规则修改
        String newCluster = decision.getRecommendedCluster();

        // 创建一个用于操作k8s自定义资源的API客户端实例
        CustomObjectsApi api = new CustomObjectsApi(karmadaClient);

        int maxRetries = 3;
        int attempt = 0;
        boolean success = false;

        while (attempt < maxRetries && !success) {
            attempt++;
            try {
                // 1️⃣ 读取现有 ResourceBinding，获取命名空间范围内ResourceBinding对象
                Map<String, Object> binding = (Map<String, Object>) api
                        .getNamespacedCustomObject(
                                "work.karmada.io",    // 资源所属api组
                                "v1alpha2",                 // 资源的API版本
                                namespace,                  // 资源所在命名空间
                                "resourcebindings",         // 资源类型名称
                                bindingName).execute();     // 具体的资源名称

                log.info("🔍 已读取 ResourceBinding [{} / {}]", namespace, bindingName);

                // 2️⃣ 获取 spec 节点，从 ResourceBinding 对象中提取 spec 字段。
                Map<String, Object> spec = (Map<String, Object>) binding.get("spec");
                if (spec == null) {
                    spec = new HashMap<>();
                    binding.put("spec", spec);
                }

                // 3️⃣ 构造新的 targetClusters 列表
                List<Map<String, Object>> clusters = List.of(Map.of("name", newCluster));
                spec.put("clusters", clusters);

                // 4️⃣ 更新 binding
                api.replaceNamespacedCustomObject(
                        "work.karmada.io",
                        "v1alpha2",
                        namespace,
                        "resourcebindings",
                        bindingName,
                        binding).execute();

                log.info("✅ 成功更新 ResourceBinding [{}] 到集群 [{}] (尝试 {} 次)",
                        bindingName, newCluster, attempt);
                log.info("""
                                ✅ ResourceBinding [{}] 更新成功！
                                • 新目标集群: {}
                                • 健康分: {}
                                • 调度原因: {}
                                """,
                        bindingName,
                        newCluster,
                        String.format("%.2f", decision.getRecommendedScore()),
                        decision.getReason());
                success = true;
            } catch (ApiException e) {
                if (e.getCode() == 409) {
                    // 冲突重试
                    log.warn("⚠️ 检测到 409 Conflict，正在重试 (第 {} 次)...", attempt);
                    try {
                        Thread.sleep(500L * attempt); // 退避等待
                    } catch (InterruptedException ex) {
                        throw new RuntimeException(ex);
                    }
                } else {
                    log.error("❌ 更新 ResourceBinding [{}] 失败: {}", bindingName, e.getResponseBody(), e);
                    break;
                }
            }
        }
        if (!success) {
            log.error("❌ 多次重试后仍未能更新 ResourceBinding [{}]，放弃。", bindingName);
        }
    }
}
