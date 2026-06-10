# PRD — actiongraph v0（类型化 GOAP ActionGraph 骨架）

> 交付对象：实现方（Codex）
> 版本：v0（不含 LLM、不含自然语言入口、不含 UI）
> 语言/构建：Java 21 + Gradle，纯库工程，**禁止引入 Spring**
> 状态：可直接实施

---

## 0. 一句话目标

用纯 Java 实现一个**确定性 GOAP ActionGraph 骨架**，证明这条链自洽：

> 类型化 Action ＋ 符号化 condition 搜索（BFS）＋ 每步执行后重规划 ＋ 失败补偿（Saga）＋ 全链路 Trace。

v0 **不接 LLM**。Goal 与初始输入硬编码。验收靠一个"续约报价"假业务场景跑通 + 单元测试。

---

## 1. 范围

### 1.1 In scope（v0 必做）
- 类型化 Action 抽象（输入/输出类型、precondition、effect、cost、riskLevel、humanReview、execute、compensate）。
- ActionRegistry（注册 / 查询）。
- GoapPlanner：基于 condition 的 **BFS** 前向搜索，确定性、可复现。
- Executor：**plan→执行一步→更新 Blackboard→重规划** 的循环；失败时反向补偿。
- Blackboard：按类型存放领域对象 + 当前成立的 condition 集合。
- Trace：结构化事件记录，至少 In-Memory 实现（SQLite 为可选加分项）。
- policy 包：接口先行、实现极薄（v0 只放占位策略，但接口必须在）。
- 一个完整可跑的续约报价示例 + 一套 JUnit 测试。

### 1.2 Out of scope（v0 明确不做，留给后续版本）
- 任何 LLM / 自然语言解析（v1）。
- 真正的权限/审批/租户策略实现（v2，仅留接口）。
- 异常自动重规划补链（v3，靠扩展 Action 即可，不在 v0 验收）。
- 上下文/长期记忆（v4）。
- A\* / 启发式（v0 用 BFS；cost 字段保留但不参与 v0 搜索排序，见 §5.4）。
- Web/UI、并发执行、分布式、持久化运行态恢复。

---

## 2. 核心设计裁决（必须严格遵守，消解歧义）

### 裁决 A：planning 是纯符号 condition 搜索，不在规划期执行 Action
Planner 只在 `Condition` 的集合空间里搜索，**绝不调用 `Action.execute()`**。规划期对每个 Action 的效果做"乐观假设"：只要 precondition ⊆ 当前 condition 集合，就认为执行后会把它的 effect 全部置 true。

### 裁决 B：类型可用性 == condition，二者统一为一张图
不要实现"类型图搜索"和"条件图搜索"两套逻辑。把"某输出类型已产出"建模为一个 effect condition（例：`CUSTOMER_PROFILE_LOADED`），把"需要某输入类型"建模为 precondition。于是 planner 只需要一种搜索。

> Action 的 `inputTypes/outputTypes` 字段仍然保留并被 Executor 用于从 Blackboard 取值/回写，但**它们不是 planner 的搜索依据**；planner 只看 preconditions/effects。实现方需保证：约定上，"输入类型 T 在 Blackboard 中存在"对应一个 condition，"输出类型 T 已写入"对应该 Action 的 effect。示例场景里我们用显式 condition 表达，见 §7。

### 裁决 C：区分"规划期静态 condition"与"运行期值断言"
- **静态 condition**：执行与否决定其真假（如 `RENEWAL_ELIGIBILITY_CHECKED`）。规划期乐观假设其会成立，参与 BFS。
- **运行期值断言（runtime guard）**：规划期不可知（如 `RenewalEligibility.eligible == true`）。**不进入 planner 的搜索**，而是作为 `Action.runtimeGuard(blackboard)`，由 Executor 在执行该 Action 前求值：
  - 通过 → 执行；
  - 不通过 → 该 Action 本步不可执行 → 触发重规划；若重规划找不到替代路径 → 运行结束，状态为 `HALTED_UNREACHABLE`（v0 不自动补链）。

### 裁决 D：单步执行 + 每步重规划（不是一次性整链跑到底）
执行循环见 §6。每执行成功一个 Action，就用最新 Blackboard 重新调用 planner。这是与"固定流程编排器"的本质区别，必须如此实现。

### 裁决 E：失败补偿按"已成功步骤的逆序"执行
某个 Action `execute` 抛异常/返回失败时，对**本次运行中已成功执行过、且声明了补偿**的 Action，按执行的**逆序**调用 `compensate`。补偿过程本身也要写 Trace。补偿失败不抛断，记 Trace 并继续补下一个（best-effort），最终运行状态 `FAILED_COMPENSATED` 或 `FAILED_COMPENSATION_INCOMPLETE`。

---

## 3. 技术栈与工程结构

- JDK 21（启用 record、sealed interface、pattern matching）。
- Gradle（Kotlin DSL 或 Groovy DSL 均可），单模块即可。
- 测试：JUnit 5 + AssertJ。
- 日志：SLF4J + 简单实现（或直接 `System.Logger`，不强制）。
- 可选依赖：`org.xerial:sqlite-jdbc`（仅当实现 SQLite Trace 时）。
- 包根：`com.actiongraph`（实现方可改，但保持单一根包）。

```
actiongraph/
├── build.gradle(.kts)
├── settings.gradle(.kts)
├── docs/PRD-v0.md
└── src
    ├── main/java/com/actiongraph
    │   ├── action
    │   │   ├── Action.java
    │   │   ├── ActionId.java
    │   │   ├── ActionRiskLevel.java
    │   │   ├── ActionRegistry.java
    │   │   ├── ExecutionContext.java
    │   │   ├── ActionResult.java
    │   │   └── CompensationResult.java
    │   ├── planning
    │   │   ├── Condition.java
    │   │   ├── Goal.java
    │   │   ├── Plan.java
    │   │   ├── PlanStep.java
    │   │   ├── Planner.java
    │   │   └── GoapPlanner.java
    │   ├── runtime
    │   │   ├── Blackboard.java
    │   │   ├── RuntimeState.java
    │   │   ├── Executor.java
    │   │   ├── RunStatus.java
    │   │   └── RunResult.java
    │   ├── policy
    │   │   ├── ExecutionPolicyGuard.java
    │   │   ├── PolicyDecision.java
    │   │   ├── HumanReviewPolicy.java
    │   │   └── PermissionPolicy.java
    │   ├── trace
    │   │   ├── TraceEvent.java
    │   │   ├── TraceEventType.java
    │   │   ├── TraceRepository.java
    │   │   ├── InMemoryTraceRepository.java
    │   │   └── SqliteTraceRepository.java   // 可选
    │   └── example/renewal
    │       ├── domain/*.java                 // CustomerId、CustomerProfile 等 record
    │       ├── service/*.java                // 假业务服务（内存模拟）
    │       ├── actions/*.java                // 5 个 Action 实现
    │       └── RenewalQuoteSampleApp.java         // main()，跑通整链
    └── test/java/com/actiongraph
        ├── planning/GoapPlannerTest.java
        ├── runtime/ExecutorTest.java
        └── example/RenewalQuoteFlowTest.java
```

---

## 4. 领域模型与核心接口（实现方按此签名实现，命名可微调）

### 4.1 Condition（符号化事实）
```java
// 一个具名布尔事实。用 key 作为身份。
public record Condition(String key) {
    public static Condition of(String key) { return new Condition(key); }
}
```
> v0 用 `String` key 即可；若想更强类型，可改 enum，但需保证示例场景与测试一致。

### 4.2 Goal
```java
public record Goal(String name, Set<Condition> targetConditions) {
    public boolean isSatisfiedBy(Set<Condition> state) {
        return state.containsAll(targetConditions);
    }
}
```

### 4.3 Blackboard
```java
public interface Blackboard {
    <T> Optional<T> get(Class<T> type);     // 按类型取领域对象
    <T> void put(T value);                   // 按其 runtime class 存入
    boolean has(Class<?> type);
    Set<Condition> conditions();             // 当前成立的 condition 快照（不可变）
    void addCondition(Condition c);
    Map<Class<?>, Object> snapshotObjects();  // 用于 trace / 调试
}
```
- v0 同一类型只存一个实例（后写覆盖先写，但需在 Trace 记 overwrite 事件）。
- `conditions()` 返回不可变副本。

### 4.4 Action
```java
public interface Action {
    ActionId id();

    Set<Class<?>> inputTypes();      // Executor 用于从 Blackboard 取值（见裁决 B）
    Set<Class<?>> outputTypes();     // 文档/校验用途；非 planner 搜索依据

    Set<Condition> preconditions();  // planner 搜索依据（静态）
    Set<Condition> effects();        // planner 搜索依据（静态，乐观假设）

    int cost();                      // v0 保留，BFS 不依赖；>=1
    ActionRiskLevel riskLevel();
    boolean requiresHumanReview();

    /** 运行期值断言（裁决 C）。默认 true 表示无额外运行期前置。 */
    default boolean runtimeGuard(Blackboard bb) { return true; }

    /** 真正执行：读 Blackboard 输入，调用业务服务，把输出 put 回 Blackboard。 */
    ActionResult execute(ExecutionContext ctx);

    /** 补偿（Saga）。无副作用的查询类返回 CompensationResult.noop()。 */
    default CompensationResult compensate(ExecutionContext ctx) {
        return CompensationResult.noop();
    }
}
```

```java
public record ActionId(String value) { /* 形如 "customer.profile.query" */ }

public enum ActionRiskLevel { READ_ONLY, LOW, MEDIUM, HIGH }

public record ActionResult(boolean success, String message,
                           List<Condition> producedConditions) {
    public static ActionResult ok(Condition... c) { /* success=true */ }
    public static ActionResult fail(String message) { /* success=false */ }
}

public record CompensationResult(boolean success, boolean noop, String message) {
    public static CompensationResult ok(String msg) { ... }
    public static CompensationResult noop() { ... }
    public static CompensationResult failed(String msg) { ... }
}
```
> 约定：`execute` 成功后，Executor 会把 `Action.effects()` ∪ `result.producedConditions()` 全部加入 Blackboard。多数 Action 的 effect 是静态固定的；`producedConditions` 给"执行后才能确定要置哪些 condition"的情况留口子（v0 示例用不到，但接口保留）。

### 4.5 ExecutionContext
```java
public interface ExecutionContext {
    Blackboard blackboard();
    TraceRepository trace();
    String runId();
}
```

### 4.6 ActionRegistry
```java
public interface ActionRegistry {
    void register(Action action);
    Collection<Action> all();
    Optional<Action> byId(ActionId id);
}
// 默认实现：内存 Map，注册重复 id 抛 IllegalStateException。
```

---

## 5. Planner 规格（v0 核心，最易做错，按此实现）

### 5.1 接口
```java
public interface Planner {
    /** 返回到达 goal 的一条计划；找不到返回 Optional.empty()。 */
    Optional<Plan> plan(Goal goal, Set<Condition> currentState, Collection<Action> actions);
}

public record Plan(List<PlanStep> steps) {
    public boolean isEmpty() { return steps.isEmpty(); }
}
public record PlanStep(ActionId actionId) {}
```

### 5.2 算法：前向 BFS
- **节点 = condition 集合（state）**。起点 = `currentState`。
- 某 state 下，Action `a` 可用 ⟺ `state.containsAll(a.preconditions())`。
- 应用 `a`：`next = state ∪ a.effects()`。
- 目标：`goal.isSatisfiedBy(state)`。
- BFS 逐层扩展，找到的**第一条**到达 goal 的路径即为 Plan（步数最少）。
- **visited 去重**：以 state 集合内容（建议规范化为有序 key 列表/字符串）为 key，避免环与重复扩展。
- 同一层内对可用 Action 的遍历顺序**必须确定**（按 `ActionId.value()` 字典序），保证规划结果可复现。
- 若起点已满足 goal → 返回空 Plan（`steps` 为空），Executor 据此判定已达成。
- 搜索上限：`maxDepth`（默认 32）与 `maxExpansions`（默认 10_000）防爆；超限按"找不到"处理并写 Trace。

### 5.3 关于 runtimeGuard
planner **完全忽略** `runtimeGuard`（裁决 C）。它只是乐观地认为：执行某 Action 后其 effects 全部成立。runtimeGuard 的真假留给 Executor 在执行时处理。

### 5.4 cost 与 A\*
v0 用 BFS（按步数最优），`cost` 字段存在但不参与排序。**预留**：日后把 BFS 换成 A\*/Dijkstra（g = Σcost，h = 0 即退化为 Dijkstra）时，接口与数据结构不需改动。实现时把"边权"集中在一处，便于后续替换。

---

## 6. Executor 规格（执行 + 每步重规划 + 补偿）

### 6.1 接口
```java
public interface Executor {
    RunResult run(Goal goal, Blackboard initial,
                  Collection<Action> actions,
                  ActionRegistry registry);
}

public enum RunStatus {
    COMPLETED,                       // goal 达成
    HALTED_UNREACHABLE,              // 重规划找不到可推进路径
    FAILED_COMPENSATED,             // 执行失败，补偿全部成功
    FAILED_COMPENSATION_INCOMPLETE  // 执行失败，补偿存在失败
}

public record RunResult(String runId, RunStatus status,
                        Set<Condition> finalState,
                        List<ActionId> executedActions,
                        String message) {}
```

### 6.2 主循环（伪码，必须按此语义）
```
runId = newId()
trace.RUN_STARTED(goal)
executed = []                       // 已成功执行、用于补偿的栈
loop (受 maxSteps=64 保护):
    state = blackboard.conditions()
    if goal.isSatisfiedBy(state):
        trace.GOAL_SATISFIED; return COMPLETED
    planOpt = planner.plan(goal, state, actions)
    if planOpt empty or plan.isEmpty-but-goal-unsatisfied:
        trace.NO_PLAN; return HALTED_UNREACHABLE
    trace.PLAN_GENERATED(plan)
    step = plan.steps[0]            // 只取第一步（裁决 D）
    action = registry.byId(step.actionId)

    // 1) 策略闸门（v0 极薄）
    decision = policyGuard.evaluate(action, blackboard)
    trace.POLICY_EVALUATED(decision)
    if decision == DENY: return HALTED_UNREACHABLE (message=策略拒绝)
    if decision == REQUIRES_HUMAN_REVIEW: trace.HUMAN_REVIEW_REQUESTED;
        // v0：默认自动批准（autoApprove=true），写 Trace 后继续；接口保留人工卡点
    // 2) 运行期值断言（裁决 C）
    if not action.runtimeGuard(blackboard):
        trace.RUNTIME_GUARD_FAILED(action)
        // 把该 action 从本轮候选中临时剔除后重规划；若仍无路 → HALTED_UNREACHABLE
        actions' = actions without this action
        replan with actions'; if none → HALTED_UNREACHABLE; else continue loop
    // 3) 执行
    trace.ACTION_STARTED(action)
    try:
        result = action.execute(ctx)
    catch ex:
        result = fail(ex)
    if result.success:
        blackboard.addConditions(action.effects() ∪ result.producedConditions())
        executed.push(action)
        trace.ACTION_SUCCEEDED(action, newConditions)
        continue loop               // 重规划
    else:
        trace.ACTION_FAILED(action, result.message)
        compensateAll(executed)     // 逆序补偿
        return FAILED_COMPENSATED / FAILED_COMPENSATION_INCOMPLETE
trace.RUN_ENDED(status)
```

### 6.3 runtimeGuard 失败的重规划细节
当某 Action 的 `runtimeGuard` 返回 false（如续约不合格），应**在本轮把该 Action 排除后重新规划**。若排除后仍能达到 goal，走新路径；若不能，返回 `HALTED_UNREACHABLE`，message 说明被哪个 guard 卡住。v0 示例中"续约不合格"就会落到 `HALTED_UNREACHABLE`——这是**预期且正确**的结果，需在测试中覆盖。

### 6.4 补偿
```
compensateAll(executed):
    allOk = true
    while executed not empty:
        a = executed.pop()          // 逆序
        try:
            cr = a.compensate(ctx)
            trace.COMPENSATED(a, cr)
            if !cr.success && !cr.noop: allOk = false
        catch ex:
            trace.COMPENSATION_ERROR(a, ex); allOk = false
    return allOk ? FAILED_COMPENSATED : FAILED_COMPENSATION_INCOMPLETE
```

---

## 7. 示例业务场景（验收载体）

### 7.1 领域类型（record，放 `example/renewal/domain`）
```java
record CustomerId(String value) {}
record CustomerProfile(CustomerId customerId, String name) {}
record CurrentContract(String contractId, CustomerId customerId, boolean nearExpiry) {}
record RenewalEligibility(boolean eligible, String reason) {}
record QuoteDraft(String quoteId, CustomerId customerId) {}
record ApprovalRequest(String approvalId, String quoteId) {}
```

### 7.2 假业务服务（`example/renewal/service`，内存模拟，可被测试替换）
- `CustomerService.findProfile(CustomerId) -> CustomerProfile`
- `ContractService.findCurrent(CustomerId) -> CurrentContract`
- `RenewalPolicyService.check(CurrentContract) -> RenewalEligibility`
- `QuoteService.createDraft(...) -> QuoteDraft` / `voidDraft(quoteId)`
- `ApprovalService.request(...) -> ApprovalRequest` / `withdraw(approvalId)`

### 7.3 五个 Action（condition 命名固定，供 planner 与测试对齐）

| Action id | precondition | effect | runtimeGuard | risk | compensate |
|---|---|---|---|---|---|
| `customer.profile.query` | `CUSTOMER_ID_PRESENT` | `CUSTOMER_PROFILE_LOADED` | — | READ_ONLY | noop |
| `contract.current.query` | `CUSTOMER_ID_PRESENT` | `CURRENT_CONTRACT_LOADED` | — | READ_ONLY | noop |
| `renewal.eligibility.check` | `CURRENT_CONTRACT_LOADED` | `RENEWAL_ELIGIBILITY_CHECKED` | — | LOW | noop |
| `quote.draft.create` | `CUSTOMER_PROFILE_LOADED`,`RENEWAL_ELIGIBILITY_CHECKED` | `QUOTE_DRAFT_CREATED` | `RenewalEligibility.eligible == true` | MEDIUM | voidDraft |
| `sales.approval.request` | `QUOTE_DRAFT_CREATED` | `SALES_APPROVAL_REQUESTED` | — | HIGH | withdraw |

- 初始 Blackboard：`CustomerId("C001")` + condition `CUSTOMER_ID_PRESENT`。
- Goal：`prepareRenewalQuote`，target = `{ SALES_APPROVAL_REQUESTED }`。
- 期望 planner 自动搜出顺序（步数最优、确定）：
  `customer.profile.query` → `contract.current.query` → `renewal.eligibility.check` → `quote.draft.create` → `sales.approval.request`
  （前两步顺序由 `ActionId` 字典序决定，确定即可。）

---

## 8. Trace 规格

### 8.1 事件类型
```java
enum TraceEventType {
    RUN_STARTED, PLAN_GENERATED, NO_PLAN,
    POLICY_EVALUATED, HUMAN_REVIEW_REQUESTED,
    RUNTIME_GUARD_FAILED,
    ACTION_STARTED, ACTION_SUCCEEDED, ACTION_FAILED,
    BLACKBOARD_UPDATED,
    COMPENSATED, COMPENSATION_ERROR,
    GOAL_SATISFIED, RUN_ENDED
}
```
```java
record TraceEvent(String runId, long seq, Instant at,
                  TraceEventType type, String actionId,
                  String detail, Map<String,String> data) {}
```
### 8.2 仓储
```java
interface TraceRepository {
    void append(TraceEvent e);
    List<TraceEvent> findByRun(String runId);
}
```
- v0 必交 `InMemoryTraceRepository`（线程安全的 list 即可）。
- `SqliteTraceRepository` 为可选加分；若做，建表 `trace_event(run_id, seq, at, type, action_id, detail, data_json)`，用 sqlite-jdbc。

---

## 9. policy 包（v0 接口先行，实现极薄）

```java
enum PolicyDecision { ALLOW, REQUIRES_HUMAN_REVIEW, DENY }

interface ExecutionPolicyGuard {
    PolicyDecision evaluate(Action action, Blackboard bb);
}
```
v0 默认实现 `DefaultPolicyGuard`：
- `action.requiresHumanReview()` 为 true → `REQUIRES_HUMAN_REVIEW`（Executor 在 v0 自动批准并记 Trace）。
- 其余 → `ALLOW`。
- `HumanReviewPolicy`、`PermissionPolicy` 仅留空接口 + 占位实现，**不写真实逻辑**（v2 再做）。

> 目的：让 §6.2 的策略闸门有真实挂点，避免日后补企业安全要改执行循环。

---

## 10. 验收标准（Definition of Done）

v0 通过，当且仅当：

1. **整链自动规划**：`RenewalQuoteSampleApp.main()` 从 `CustomerId("C001")` 出发，planner（非硬编码顺序）搜出 §7.3 的 5 步链并执行，最终 `RunStatus.COMPLETED`，Blackboard 含 `ApprovalRequest`。
2. **每步重规划**：日志/Trace 中每个 Action 成功后都有一次新的 `PLAN_GENERATED`（证明是循环重规划而非一次性整链）。
3. **runtimeGuard 生效**：把 `RenewalPolicyService` 配成"不合格"时，运行在 `quote.draft.create` 前被 guard 拦下，重规划无替代路径 → `HALTED_UNREACHABLE`，且 `quote.draft.create` 未被执行。
4. **补偿生效**：注入一个让 `sales.approval.request.execute` 抛异常的场景 → 触发逆序补偿，`quote.draft.create` 的 `voidDraft` 被调用，状态 `FAILED_COMPENSATED`，Trace 含 `COMPENSATED`。
5. **确定性**：相同输入多次运行，planner 产出的步骤序列完全一致。
6. **测试**：`GoapPlannerTest`（纯搜索，不执行 Action）、`ExecutorTest`（含补偿/guard 分支）、`RenewalQuoteFlowTest`（端到端）全绿；`./gradlew build` 通过。
7. **无 LLM、无 Spring、无网络依赖**；`./gradlew run`（或 `java -jar`）可离线跑通。

### 10.1 必交测试用例清单
- planner：起点已满足 goal → 空 Plan；正常 5 步链；缺 Action 导致不可达 → empty。
- planner：visited 去重 / maxDepth 限制不死循环。
- executor：happy path → COMPLETED。
- executor：runtimeGuard 失败 → HALTED_UNREACHABLE 且后续 Action 未执行。
- executor：execute 抛异常 → 逆序补偿，状态正确，Trace 完整。
- registry：重复 id 注册抛异常。

---

## 11. 里程碑（v0 详细，v1+ 仅占位）

- **v0（本 PRD）**：类型化 Action + GOAP(BFS) + 每步重规划 + 补偿 + Trace，无 LLM。
- **v1**：LLM Goal Interpreter——自然语言 → `GoalType + GoalParameters + MissingFields + ClarificationQuestion`；LLM **不生成 Plan**。
- **v2**：Policy/HumanReview 实化——高风险卡点、审批/租户/用户权限、执行前预览、执行后审计。
- **v3**：异常自动重规划补链（缺合同→建草稿 等）。**应仅靠新增 Action + Condition 实现，不改 Executor/Planner 核心**——这是 v0 架构正确性的反向验证。
- **v4**：上下文/长期记忆（结构化优先，暂不上向量库）。

---

## 12. 给实现方的注意事项

- 先写 `planning` + 测试（纯函数、最易验证），再写 `runtime`，最后写 `example`。
- planner 与 executor **解耦**：planner 不持有 Blackboard 可变引用，只接收 `Set<Condition>` 快照。
- 所有 record 不可变；Blackboard 是唯一可变状态持有者。
- 不要为了"通用"提前抽象多 Goal/多 Agent；v0 只服务单 Goal 单运行。
- 命名可微调，但 §7.3 的 condition key 与 ActionId 字符串建议保持一致，方便对照测试。
- 把"边权/搜索策略"集中可替换，给 v 后续的 A\* 留位（§5.4）。

---

## 13. 开放问题（实现前可与需求方确认，但有默认值不阻塞）

| # | 问题 | v0 默认 |
|---|---|---|
| 1 | SQLite Trace 是否必须 | 否，In-Memory 即可，SQLite 加分 |
| 2 | Condition 用 String 还是 enum | String（示例 key 固定） |
| 3 | 同类型多实例的 Blackboard | v0 单实例覆盖即可 |
| 4 | human review 在 v0 是否真卡 | 否，自动批准 + 记 Trace |
| 5 | 构建产物形态 | 库 + 可运行 sample main，二选一不强制打 jar |
