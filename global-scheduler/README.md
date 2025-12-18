# Global Scheduler (GS) 数据流快速指南

下文聚焦从 LSA 上报数据到评分输出的完整链路，便于开发、运维和调参。

## 数据上报入口
- `POST /api/clusters/report` → `MemberClusterController.reportStatus`
- 入参：`ClusterStatus`（当前仅包含网络相关字段：`peerRawStats`、`clusterName` 等）。

## 处理流水线
1) **接收与入线**
   - 控制器调用 `MemberClusterService.updateClusterStatus`。

2) **网络指标聚合** (`MemberClusterServiceImpl.aggregateNetworkMetrics`)
   - 输入：LSA 的 `peerRawStats`（邻居 -> 原始 RTT/丢包）。
   - 输出：
     - `networkLatency`：健康链路的平均 RTT（无健康链路则惩罚为 2000ms）。
     - `packetLossRate`：有健康链路则记 0，否则用原始丢包均值（缺数据默认为 100）。
     - `peerLatencyMap`：展示/亲和用的延迟 = RTT + 丢包率 * 20。

3) **异常检测与稳定性预测** (`AnomalyDetectionServiceImpl`)
   - 静态异常分：基于当前 `networkLatency`、`packetLossRate` → `anomalyScore`。
   - EWMA 预测：未来延迟/丢包 → 生成 `stabilityScore`（0~100，越高越稳）。
     - 预测延迟高：递增扣分；预测丢包：直接大幅扣分；静态异常高：再减分。
   - 备注 `remark` 记录预测结果。

4) **健康分（当前态）** (`HealthEvaluator`)
   - 仅看当前网络：70% 延迟 + 30% 丢包 → `healthScore`（0~100），并生成 `healthStatus`（Healthy/Warning/Critical）。

5) **缓存**
   - 更新内存 `clusterMap`，供后续查询和打分。

6) **调度评分** (`ClusterScoreServiceImpl.calculateScore`)
   - 基础分 `baseScore` = 0.7 * `stabilityScore` + 0.3 * `healthScore`。
   - 亲和性加分：若指定目标集群，查 `peerLatencyMap`，按 `20 * e^(-lat/30)`（<900ms 才加）；同集群固定 +20。
   - 熔断：`stabilityScore < 60` 时最终分上限 55。
   - 返回的 `ClusterScore.healthScore` 字段即“最终调度分”。

## 关键类与位置
- 控制层：`controller/MemberClusterController.java`
- 服务层：`service/impl/MemberClusterServiceImpl.java`
- 异常&预测：`service/impl/AnomalyDetectionServiceImpl.java`
- 健康评估：`scheduler-common/util/HealthEvaluator.java`
- 评分引擎：`service/impl/ClusterScoreServiceImpl.java`

## 指标含义速查
- `networkLatency`：聚合后的当前平均 RTT。
- `packetLossRate`：聚合后的当前丢包率（健康链路存在则为 0）。
- `peerLatencyMap`：面向展示/亲和性的延迟矩阵。
- `anomalyScore`：当前异常程度（越高越异常）。
- `stabilityScore`：预测未来稳定性（越高越稳）。
- `healthScore`（ClusterStatus）：当前网络健康分。
- `healthScore`（ClusterScore）：最终调度分（命名历史原因，可视作 `finalScore`）。

## 调参与改造建议
- 熔断与权重可提取为配置，便于无代码重调。
- 若需进一步简化，可：
  1) 将最终调度分重命名为 `finalScore`，减少歧义。
  2) 在 Dashboard/日志中并列输出三类分：健康分、稳定分、最终分。
