# PRD — MS2 异步外部事件等待（挂起等回调 / 事件恢复 / 等待超时）

> 交付对象：实现方（Codex）
> 前置版本：MS1 完成后的 main（385 tests）
> 战略地位：金融跨服务交互的第二根支柱。核心系统、影像平台、征信接口大量是「提交请求 → 分钟级到天级后回调/MQ 通知」的异步形态；没有本期能力，这类集成只能靠业务方自己轮询或阻塞线程。
> 复用基线：人审挂起已经是"等外部决定"的特例——MS2 把它泛化为"等任意外部事件"，挂起/认领/恢复/崩溃恢复机制全部复用。
> 状态：可直接实施

---

## 0. 一句话目标

Action 发出异步请求后声明「等待事件 X」，运行持久挂起（不占线程、可跨重启）；外部系统回调送达事件后，运行从挂起点恢复继续；事件超时未达则按既有补偿语义冲正。

## 0.1 兼容性硬约束

- 事件能力完全 opt-in：没有 Action 返回 waiting 结果时，行为与当前零差异；现有 385 测试全绿；
- `PolicyDecision` 不加值；**`RunStatus` 本期加一个值 `SUSPENDED_WAITING_EVENT`**（v2 以来首次，理由见裁决 A）；
- 新公共 API 全部 `@Experimental`；japicmp 通过（枚举加值为兼容变更）。

## 0.2 落笔前事实核对

- 当前 `ActionResult` 形状是 `record ActionResult(boolean success, String message, List<Condition> producedConditions)`，仅有 `ok(...)` / `fail(...)` 工厂；MS2 的 waiting 形态必须 additive，保留现有构造器与 ok/fail 语义。
- 当前人审 callback 配置类是 `ActionGraphHumanReviewCallbackProperties`，前缀 `actiongraph.human-review.callback-endpoint`，字段为 `enabled`、`path`、`tokenHeader`、`sharedSecret`；默认 path 是 `/actiongraph/human-review/callbacks`，默认 token header 是 `X-ActionGraph-Review-Token`。事件 callback 端点镜像这套形态，但使用自己的 `actiongraph.events.callback-endpoint` 前缀与事件专用默认 header。

---

## 1. 范围

### 1.1 In scope
- MS2-A Action 声明等待：`ActionResult.waiting(...)` 变体与执行器挂起路径；
- MS2-B 事件投递：`ExternalEventGateway` + 按事件类型注册的 `EventApplier`（payload → 类型化 Blackboard 事实）；
- MS2-C 关联键模型与原子认领（重复/迟到投递安全）；
- MS2-D 等待超时清扫器（超时 → 既有补偿路径）；
- MS2-E starter：HTTP 回调端点（镜像人审 callback 的 path/shared-secret/幂等形态）+ 清扫调度 + 配置项；
- 与 MS1 崩溃恢复的交互（裁决 D，本期最关键的正确性点）；
- 扩展点手册新增「异步事件集成」一章（含 MQ 监听器调用 Gateway 的示例代码）。

### 1.2 Out of scope
- Kafka/RocketMQ 等具体 MQ 适配模块（Gateway 是普通 Java 接口，任何监听器两行可调；适配模块等试点需求定形）；
- 一个运行同时等待多个事件（本期一次只等一个；多路等待留待真实场景出现）；
- 事件负载的 Schema 校验/注册中心；
- 教学项目第 8 幕（验收后另行安排）。

---

## 2. 核心裁决

### 裁决 A：新增 `RunStatus.SUSPENDED_WAITING_EVENT`
等人和等系统必须对调用方可区分：前者去催审批人，后者去查外部系统——复用 `SUSPENDED_PENDING_REVIEW` 是对调用方撒谎。这是 v2 以来首次动 `RunStatus`，属于"新等待形态"级别的语义扩展，符合当初保留枚举稳定的本意。samples 中所有对 `RunStatus` 的 switch 同步补分支。

### 裁决 B：关联键由 Action 生成，框架只负责匹配
```java
// ActionResult additive（兼容构造器保留，现有 ok/fail 语义不变）：
public static ActionResult waiting(String eventType, String correlationId,
                                   Duration timeout, String message);
```
- 键 = `(eventType, correlationId)`，全局唯一指向一个等待中的运行；
- correlationId 由 Action 决定——通常就是外部系统返回的受理号/任务号（外部系统回调时天然会带它，这是键归属 Action 的根本原因）；
- 同键已有等待中的运行 → 本次 waiting 按 Action 失败处理（数据错误，走补偿）；
- `timeout` 可空，空则取配置 `actiongraph.events.default-timeout`（默认 24h）。

### 裁决 C：waiting 的副作用语义——「已发出」是真实副作用
Action 返回 waiting 时，外部请求已经发出。因此执行器必须：
1. **应用该 Action 声明的 effects**（如 `JOB_SUBMITTED`——发出这件事是真的）；
2. **将该 Action 压入补偿栈**（超时/后续失败时，其 compensate 负责撤回外部请求；补偿契约沿用"容忍对端可能已完成"）；
3. flush trace（`EVENT_WAIT_STARTED`，含 eventType/correlationId/deadline）；
4. 快照落库：`SnapshotState.WAITING_EVENT` + 事件键 + 截止时间；
5. 返回 `SUSPENDED_WAITING_EVENT`，**不重规划、不占线程**。

### 裁决 D：事件先持久折入快照，再继续执行（与 MS1 的关键交互）
投递流程（`ExternalEventGateway.deliver`）：
```
① 原子认领：WAITING_EVENT → RESUMING（按事件键；认领失败 → 返回结果码，不抛异常）
② EventApplier.apply(payload, blackboard)：负载 → 类型化对象 + conditions
③ 快照回写落库（state=RUNNING，含已折入事件的 Blackboard）   ← 本裁决的核心
④ 进入既有 runLoop 继续（重新规划，事件产出的 condition 推动链路前进）
```
**③ 必须在 ④ 之前完成**：若进程在 ③ 之后任意时刻崩溃，MS1 恢复器拿到的快照已包含事件——
事件源（MQ/回调方）收到成功响应后即可丢弃消息，**不存在"事件被消费但未生效"的窗口**。
若崩溃发生在 ③ 之前，认领的 RESUMING 会因 stale 被重新认领回 WAITING_EVENT 语义？——否：
stale RESUMING 由既有 claimForResume 兜底重续，但此时事件未折入。因此 ②③ 失败/崩溃时投递方会收到错误或超时，**按未投递处理重发**（at-least-once 前提下安全：重发走 ① 的原子认领）。手册必须写明：调用 Gateway 的一方在收到成功返回前不得 ack 消息。

### 裁决 E：迟到与重复投递——结果码，不是异常
```java
public enum DeliveryResult { RESUMED, ALREADY_HANDLED, NOT_FOUND, APPLIER_MISSING }
```
- 重复投递：第二次认领失败 → `ALREADY_HANDLED`（幂等，MQ at-least-once 安全）；
- 运行已超时/已完成：`NOT_FOUND`（投递方按业务策略记录，常见为忽略）；
- 未注册对应 `EventApplier`：`APPLIER_MISSING`（配置错误，投递方应告警）。
副作用恰好一次由原子认领保证，与 resume 竞争同一套测试模式验收。

### 裁决 F：超时 = 失败走补偿，不是"结果未知"
等待超时与 MS1 的崩溃窗口本质不同：**请求确认已发出**（effects 已应用、补偿已入栈），只是回复未到。清扫器认领过期等待 → 直接走既有"逆序补偿"路径（waiting Action 的 compensate 撤回外部请求）→ 终态 `FAILED_COMPENSATED`，trace 记 `EVENT_WAIT_TIMED_OUT`。不引入"超时后转人工"等策略分支——试点有需求再加。

---

## 3. 模型与接口（additive 清单，仅限以下）

1. `ActionResult` + `eventType/correlationId/timeout` 字段与 `waiting(...)` 工厂（兼容构造器保留）；
2. `RunStatus` + `SUSPENDED_WAITING_EVENT`；`SnapshotState` + `WAITING_EVENT`；
3. `SuspendedRun` + `eventType`、`eventCorrelationId`、`eventDeadline`（兼容构造器，非事件快照三者为 null）；
4. `SuspendedRunRepository` + default：`claimWaitingEvent(eventType, correlationId)`、`claimExpiredWaiting(Instant now)`（语义同既有 claim 家族：原子、失败返回 empty）；
5. `TraceEventType` + `EVENT_WAIT_STARTED`、`EVENT_DELIVERED`、`EVENT_WAIT_TIMED_OUT`；
6. 新包 `com.actiongraph.events`（全部 `@Experimental`）：
```java
public record EventPayload(String contentType, String body, Map<String,String> attributes) {}
public interface EventApplier {
    String eventType();
    void apply(EventPayload payload, Blackboard blackboard);   // 解析归域，框架不猜
}
public final class ExternalEventGateway {                       // 组合 executor+repository+appliers，模式同 RunRecoverer
    public DeliveryResult deliver(String eventType, String correlationId, EventPayload payload);
}
public final class EventWaitSweeper { public int sweepOnce(Instant now); }
```
7. starter 配置：
```yaml
actiongraph:
  events:
    default-timeout: 24h
    sweep-period: 60s            # 0 = 不调度
    callback-endpoint:
      enabled: false             # POST {path}/{eventType}/{correlationId}
      path: /actiongraph/events
      shared-secret: ...         # 镜像人审 callback：共享密钥 + 重复投递幂等
      token-header: X-ActionGraph-Event-Token
```

## 4. JDBC 变更

- 快照表加列：`event_type`、`event_correlation_id`、`event_deadline`（兼容迁移，旧行为 null）；
- 唯一索引 `(event_type, event_correlation_id)`（WHERE state='WAITING_EVENT' 语义唯一；H2 不支持部分索引则在认领 SQL 层保证）；
- 认领 SQL 复用既有原子 UPDATE 模式。

## 5. 与既有机制的交互（实现方必读）

- **MS1 恢复器**：WAITING_EVENT 快照无心跳、不会被 stale-RUNNING 认领（等待不占进程，天然跨重启）；事件认领后的执行段受 MS1 检查点保护；
- **人审**：事件恢复后续跑遇到 HIGH 动作照常转 SUSPENDED_PENDING_REVIEW——两种挂起可在同一运行中先后发生（测试覆盖）；
- **重试/超时（DX1-C）**：作用于"发出请求"那次同步调用，与事件等待正交；waiting 结果不参与重试；
- **脱敏**：EventPayload 进 trace 的部分过 maskText/maskData（attributes 同理）；
- **审计**：`EVENT_DELIVERED` 记录 payload 摘要（脱敏后、截断至配置长度），不落全量原文。

## 6. 验收标准（Definition of Done）

1. **回归**：无 waiting Action 时行为零变化，现有 385 测试全绿。
2. **happy path**：Action 返回 waiting → `SUSPENDED_WAITING_EVENT`、快照 WAITING_EVENT 含键与截止时间、effects 已应用、补偿栈含该 Action；`deliver` → applier 产出的对象与 condition 进入 Blackboard → 续跑 `COMPLETED`；trace 含 `EVENT_WAIT_STARTED`/`EVENT_DELIVERED`，哈希链跨等待恢复有效。
3. **并发/重复投递**：两线程同键同时 deliver → 恰一个 `RESUMED`、另一个 `ALREADY_HANDLED`，副作用恰好一次（屏障并发，复用 resume race 模式）；事后再投 → `NOT_FOUND`。
4. **超时**：截止过期 → `sweepOnce` 认领 → waiting Action 的 compensate 被调用（撤回外部请求）→ `FAILED_COMPENSATED` + `EVENT_WAIT_TIMED_OUT`；未过期不被清扫。
5. **裁决 D 正确性（本期最关键测试）**：deliver 在 ③ 快照回写完成后、④ 续跑前"崩溃"（测试仓储在回写后抛错）→ MS1 恢复器认领续跑至 `COMPLETED`，**事件不需要重投**；deliver 在 ③ 之前崩溃 → 重投同一事件成功恢复，副作用恰好一次。
6. **两种挂起接力**：等待事件恢复 → 续跑触发人审挂起 → 审批 → resume → `COMPLETED`（一条运行先后经历两种挂起，trace 连续）。
7. **JDBC**：跨"重启"（新仓储实例）投递成功；`APPLIER_MISSING` 路径；回调端点：错误密钥 401、重复 POST 幂等返回 `ALREADY_HANDLED`。
8. **规范**：samples 的 RunStatus switch 补全；新 API `@Experimental`；japicmp、`./gradlew build --rerun-tasks` 全绿。

## 7. 实施顺序

A（waiting 结果与挂起路径）→ C（键模型与原子认领）→ B（Gateway+Applier，含裁决 D 的折入时序）→ D（清扫器）→ E（starter 端点与调度）→ 手册。

## 8. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | 一运行多事件并行等待 | 不做，串行等待够覆盖试点场景 |
| 2 | 超时后转人工而非补偿 | 不做，策略位留给试点反馈 |
| 3 | payload 全文存档 | 不存，只存脱敏摘要；需要全文的域自行落业务表 |
| 4 | MQ 适配模块 | 不做，手册给监听器调用示例 |
