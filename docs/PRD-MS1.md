# PRD — MS1 运行持久性与跨服务交互（步级检查点 / 崩溃恢复 / 幂等约定）

> 交付对象：实现方（Codex）
> 前置版本：DX1 完成后的 main（376 tests，10 模块）
> 战略地位：**头等优先级**。微服务（T2 编排服务）形态的准入条件——不补本期内容，编排器进程一次滚动发布就可能制造"远端副作用已发生、本端无人补偿"的资损事故。
> 架构总纲：**集中编排、分布执行**——分布的是 Action（远程调用），永远不分布规划器与 Blackboard。
> 状态：可直接实施

---

## 0. 一句话目标

让编排器进程在**任意时刻被 kill -9**，重启后所有在途运行要么从检查点继续完成、要么完整冲正——副作用零孤儿，审计链零断裂。

## 0.1 总裁决：检查点是挂起快照的推广，不是新机制

挂起（等审批）和检查点（防崩溃）持久化的是同一个东西：**运行快照**（Blackboard + 补偿栈 + 已执行清单）。区别只是触发时机与状态标记。因此：

- **复用** `SuspendedRun` 模型与 `SuspendedRunRepository`（含既有 JDBC 表），不建第二套存储；
- 快照状态机扩展为：`RUNNING`（在途检查点）→ `SUSPENDED`（等审批）→ `RESUMING`（已认领）→ 删除（终态）；
- 既有挂起/认领/恢复语义零改变——现有全部测试不改语义通过。

## 0.2 兼容性硬约束

- `RunStatus`、`PolicyDecision` 枚举不加值；
- durability **默认关闭**（`actiongraph.durability.enabled=false`），关闭时行为与当前完全一致（T1 嵌入式用户零感知）；
- 开启 durability 要求 JDBC 仓储（内存仓储 + durability 仅用于测试）；
- 新公共 API 全部 `@Experimental`。

---

## 1. 范围

### 1.1 In scope
- MS1-A 步级检查点：每个 Action 成功后持久化运行快照；
- MS1-B 执行意图（WAL）：执行前登记 in-flight action，崩溃窗口可识别；
- MS1-C 崩溃恢复器：心跳 + 僵尸认领 + 从检查点续跑/冲正；
- MS1-D 幂等键约定与 helper；
- MS1-E 检查点与 trace 攒批的合并写（性能裁决）；
- 性能基准（验收含数字）；扩展点手册新增「跨服务与崩溃恢复」一章。

### 1.2 Out of scope（后续 MS 阶段，本期明确不做）
- MS-2 异步外部事件等待（挂起等 MQ/核心回调的泛化）；
- MS-3 远程 Action 目录与代理（control-plane 自注册）；
- 跨运行时 A2A、Outbox/事务消息、分布式 exactly-once（用"补偿+幂等"组合替代，见裁决 C）。

---

## 2. 核心裁决

### 裁决 A：每步两次写，合并为每步一个事务
每个 Action 的持久化时序（JDBC，单连接单事务）：

```
执行前:  UPDATE snapshot SET in_flight_action=:id, heartbeat_at=now   -- 意图登记(WAL)
执行成功: 同一事务内：
         ① flush trace 缓冲（本步全部事件，含哈希链）
         ② UPSERT snapshot（Blackboard+补偿栈+executedActions，in_flight_action=NULL，state=RUNNING）
```

- trace 的既有 flush 点（挂起前/终态/补偿前/审批前）保持不变，新增"每步成功后"——审计实时性同时受益；
- 这把 durability 开销钉在**每步 2 个 DB 往返**；验收要求给出实测数字（§7.6）。

### 裁决 B：恢复时，in-flight 步按「结果未知」处理——与 C2 同一哲学
崩溃窗口里只有一种不确定：意图已登记、检查点未写上。恢复时：

1. 该 action **先压补偿栈并立即执行其 compensate**（补偿契约本就要求容忍"正向操作可能从未发生"，DX1 手册已写明）；
2. 补偿后从检查点状态**重新规划**——若仍需要该 effect，规划器会重新选中该 action 干净重做。

即「补偿 + 重做」实现等效 exactly-once，不引入 outbox 复杂度。幂等键（裁决 D）让下游去重，使这一步更便宜，但**框架不假设下游幂等**。

### 裁决 C：恢复策略全局可配，默认续跑
`actiongraph.durability.recovery = CONTINUE | COMPENSATE`，默认 `CONTINUE`：

- `CONTINUE`：处理 in-flight（裁决 B）后从检查点重新规划继续——GOAP 从任意 condition 状态找路是天生能力，无需流程游标；
- `COMPENSATE`：处理 in-flight 后将已执行动作全部逆序冲正，终态 `FAILED_COMPENSATED`（保守机构的选项）。

### 裁决 D：幂等键是约定 + helper，不是强制
```java
// core，com.actiongraph.durability
public record IdempotencyKey(String runId, String actionId, int attempt) {
    public String asHeaderValue();                  // "runId/actionId/attempt"
}
// ExecutionContext additive：default int attempt() { return 1; }
//（重试时由 executor 注入真实 attempt）
```
标准头名：`X-ActionGraph-Idempotency-Key`。手册给出 Feign/RestClient 拦截器示例。框架不强制——金融下游接口五花八门，约定 + 示例 + 验收清单是现实最优。

### 裁决 E：心跳独立于检查点
长动作（单步超过僵尸阈值）不能被误认领。执行器在 run 期间以虚拟线程按 `heartbeat-interval`（默认 30s）刷新 `heartbeat_at`；僵尸判定阈值 `stale-after`（默认 5 分钟）≫ 心跳间隔。两参数均可配，并复用既有 claim 的原子 UPDATE 模式（`WHERE state='RUNNING' AND heartbeat_at < :stale`）。

### 裁决 F：恢复器是 starter 的调度组件，不进 core 执行循环
`RunRecoverer`（core，纯逻辑）：认领一个僵尸快照 → 按裁决 B/C 处理 → 返回结果。starter 以固定延迟调度（默认 60s，可关），多实例并发恢复天然安全——认领是原子的（复用既有 claim 语义与测试模式）。

---

## 3. 模型与接口变更（additive 清单，仅限以下）

1. `SuspendedRun` + `snapshotState`（RUNNING/SUSPENDED）、`heartbeatAt`、`inFlightActionId`（带兼容构造器，旧语义默认 SUSPENDED）；
2. `SuspendedRunRepository` + default 方法：`saveCheckpoint(...)`、`markInFlight(runId, actionId)`、`heartbeat(runId)`、`claimStaleRunning(staleBefore)`（内存实现 CAS，JDBC 实现原子 UPDATE；不实现 default 抛 UnsupportedOperation——只有开启 durability 才会被调用）；
3. `ExecutionContext` + `default int attempt()`；
4. `TraceEventType` + `RUN_CHECKPOINTED`、`RUN_RECOVERED`（恢复时记录：从哪个检查点、in-flight 是谁、采取了什么策略）；
5. 新包 `com.actiongraph.durability`：`IdempotencyKey`、`RunRecoverer`、`RecoveryPolicy`（全部 `@Experimental`）；
6. starter 配置：
```yaml
actiongraph:
  durability:
    enabled: true            # 默认 false
    recovery: CONTINUE       # 或 COMPENSATE
    heartbeat-interval: 30s
    stale-after: 5m
    recoverer-period: 60s    # 0 = 不调度（由外部触发 RunRecoverer）
```

---

## 4. JDBC 变更

- 既有 suspended-run 表加列：`snapshot_state varchar(16)`（兼容迁移：旧行视为 SUSPENDED）、`heartbeat_at`、`in_flight_action_id`；
- 认领 SQL 扩展到 stale RUNNING；既有 SUSPENDED/RESUMING 语义不变；
- 检查点 UPSERT 与 trace 批量 INSERT **同一连接同一事务**（裁决 A）；事务失败 = 本步失败，走既有失败补偿路径（副作用已发生 → 该 action 在补偿栈中，语义自洽）。

---

## 5. 与既有机制的交互（实现方必读）

- **挂起**：等审批挂起时快照已存在（state=RUNNING），转为 SUSPENDED 是一次 UPDATE，不再是全量首写；
- **重试**：attempt 进 ExecutionContext；意图登记在**每次尝试前**刷新（in-flight 含 attempt 信息进 trace，不进表结构）；
- **超时（C2）**：超时后的补偿路径照旧；检查点在补偿完成后随终态删除快照；
- **审批多级/限额/脱敏**：零交互，不许动。

---

## 6. 性能预算（验收的一部分）

- 基准环境 H2（与现有 JDBC 测试一致）+ 5 步链：对比 durability 开/关的每运行耗时；
- **预算红线：每步 durability 开销 ≤ 5ms（H2 本地）**；超预算需给出分析与优化（首选：快照序列化复用、单条 UPSERT 合并意图清除）；
- 复跑既有并发冒烟（200 虚拟线程 × 50 runs，durability on，H2）：零失败、吞吐数字写入交付说明。

---

## 7. 验收标准（Definition of Done）

1. **回归**：durability 关闭时现有 376 测试全绿、行为零变化。
2. **kill -9 等效恢复（核心场景）**：5 步链执行到第 3 步后模拟进程消失（测试内：丢弃 executor，不走任何终态路径，快照遗留 state=RUNNING + 过期心跳）→ 新实例的 `RunRecoverer` 认领 → `CONTINUE` 策略下续跑至 `COMPLETED`；断言：最终副作用各恰好一次、trace seq 跨恢复连续、哈希链 `valid=true`、含 `RUN_RECOVERED` 事件。
3. **in-flight 结果未知**：快照带 `in_flight_action=transfer 类草稿动作` → 恢复时该动作 compensate 被调用，随后重做成功；最终草稿恰好一份、被冲正的恰好一份（可凭 voided 清单断言）。
4. **COMPENSATE 策略**：同场景配置 COMPENSATE → 全链逆序冲正，终态 `FAILED_COMPENSATED`。
5. **并发恢复竞争**：两个 RunRecoverer 同时认领同一僵尸 → 仅一个成功（复用既有 race 测试模式）。
6. **心跳防误杀**：单步耗时 > stale-after 但心跳正常 → 不被认领。
7. **性能**：§6 预算达标，数字写入交付说明。
8. **幂等 helper**：`IdempotencyKey.asHeaderValue()` 格式测试 + 手册含拦截器示例；ExecutionContext.attempt() 在重试中正确递增。
9. `./gradlew build --rerun-tasks` 全绿；新 API 全部 `@Experimental`；japicmp 通过。

## 8. 实施顺序

A（快照状态机扩展 + 每步检查点）→ E（与 trace 合并写）→ B（意图登记与恢复语义）→ C/F（恢复器 + starter 调度）→ D（幂等 helper 与手册）→ 基准。

## 9. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | 检查点是否可按 action 风险等级选择性跳过（READ_ONLY 不落） | 本期不做，全量落——正确性优先，优化留给基准数据说话 |
| 2 | recoverer 多实例的调度抖动 | 不协调，靠原子认领天然去重 |
| 3 | 快照保留策略（终态后） | 沿用现状：终态删除；审计留在 trace |
| 4 | MS-2 异步事件是否复用 snapshot_state | 是，预留枚举位 WAITING_EVENT，本期不实现 |
