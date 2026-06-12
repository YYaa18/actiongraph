# PRD — DX2 金路径与使用阶梯（正门 Bean / 正典裁决 / 文档重组）

> 交付对象：实现方（Codex）
> 前置版本：0.2.0-SNAPSHOT main（415 tests）
> 动机：能力已是 Spring 级，但**框架从未宣布过自己的正典**——注册 Action 有 4 种方式、Goal 有 3 种、Hello World 要注入 12 个 Bean、36 个文档没有阅读顺序。每个 PRD 都新增"更好的方式"，旧方式从不降级、新方式从不加冕。DX2 不新增能力，只补三个裁决：哪种写法是正统、哪个类是正门、先学什么后学什么。
> 状态：可直接实施

---

## 0. 一句话目标

让新接入方的前 15 分钟长这样：**加一个 starter → 写一个注解类 → 注入一个 Bean → `actionGraph.start(...)` 跑通**——并且在每个后续阶段都确切知道"下一步学什么"。

## 0.1 兼容性硬约束

- **不删除任何现有方式**：接口实现、Contribution、手写 GoalDefinition 全部保留——降级发生在文档与措辞，不发生在代码；
- 现有 415 测试全绿；新公共 API `@Experimental(since="0.2.0")`；japicmp 通过；
- claims 域继续冻结。

---

## 1. 范围

### 1.1 In scope
- DX2-A 正门 Bean：`com.actiongraph.ActionGraph` 门面（start / chat / resume）+ Builder + starter 自动装配；
- DX2-B 金路径裁决文档：`docs/frameworkization/golden-path.md` + 全文档四层分级标注；
- DX2-C 使用阶梯：quick-start.html 重构为 L0–L4 + `docs/learning-path.md` 阅读地图；
- DX2-D dogfood：samples/renewal 迁移到"注解三件套 + 正门"（行为不变）；
- `ActionGraphRuntimeApiService` 改为委托正门（HTTP 适配层定位不变）。

### 1.2 Out of scope
- 删除/废弃任何注册方式（最早 1.0 后按采用数据再议）；
- 正门上的审批/事件运维操作（见裁决 D）；
- preview/图导出进正门（开放问题 #1）；
- 教学项目 transfer-demo 迁移（仓库外，验收后另行处理）；
- 多租户/安全上下文透传（试点反馈后定形）。

---

## 2. 核心裁决

### 裁决 A：正典 = 注解三件套 + 正门，其余降级有名分
四层名分，全部文档按此标注（页首加层级徽章）：

| 层 | 内容 | 文档措辞 |
|---|---|---|
| **Golden Path（正典）** | `@ActionGraphAction` / `@ActionGraphGoal` / `@ActionGraphGoalSeeder` + starter 扫描 + `ActionGraph` 正门 | "推荐方式"，quick-start 只展示这一种 |
| **Packaging（打包）** | `ActionGraphContribution` | "多模块 / 类库分发场景使用" |
| **SPI（底座）** | 实现 `Action`/`GoalBlackboardSeeder` 接口、手写 `GoalDefinition` | "注解最终编译到的合同；框架扩展者使用" |
| **Internal（内部）** | `@Internal` 标注物 | 不出现在用户文档 |

### 裁决 B：正门类名 `ActionGraph`，落根包 `com.actiongraph`
- 先例：Flyway 的正门就是 `org.flywaydb.core.Flyway`——产品名即正门类名，最大可发现性；已确认无命名冲突，根包当前无顶级类，它将是接入方看到的第一个类；
- 非 Spring 用户走 `ActionGraph.builder()`（聚合 interpreter / catalog / seeders / executor / registry，全部已有 Bean 同款默认）；
- starter 提供 `@ConditionalOnMissingBean` 的 `ActionGraph` Bean。

### 裁决 C：start 与 chat 的错误哲学不同——程序员错误抛异常，用户输入给结果
```java
public final class ActionGraph {
    public static Builder builder();

    /** 代码发起：goalType 未注册 / 缺必填参数 = 调用方代码错误 → 抛 ActionGraphInputException */
    public RunResult start(String goalType, Map<String, String> parameters);

    /** 自然语言发起：缺参是对话的正常分支 → 返回结果，绝不抛 */
    public ChatResult chat(String input);
    public ChatResult chat(String input, Map<String, String> knownParameters);   // 多轮补参

    /** 发起面的另一半：审批/事件后继续。底层语义 = GoapExecutor.resume */
    public RunResult resume(String runId);
}

public record ChatResult(
        GoalInterpretation interpretation,
        @Nullable RunResult run                  // started()==false 时为 null
) {
    public boolean started();
    public ClarificationQuestion clarification();  // 未 started 时的追问
}
```
- `start` 内部：catalog 查 GoalDefinition → 建 `InMemoryBlackboard` → SeederRegistry 灌注 → executor.run(goal, bb, registry.all(), registry)；
- `chat` 内部：interpreter.interpret → isReady 则走 start 同路径，否则封装澄清返回；
- 两个入口共用同一条执行私径，杜绝行为分叉。

### 裁决 D：正门保持窄——只管"发起与继续"，不管"运营"
审批决定（`HumanReviewRepository.decide`）、事件投递（`ExternalEventGateway.deliver`）、恢复扫描（`RunRecoverer`）是**运营面**，使用者是审批系统/MQ 监听器/调度器，不是业务代码——它们不进正门。`resume` 进正门是因为它是发起面的收尾（业务代码在审批后确实要调它）。这条线写进 golden-path.md，防止正门日后长成上帝对象。

### 裁决 E：`ActionGraphRuntimeApiService` 降为 HTTP 适配层
其 interpret/start 逻辑与正门重复——重构为**委托 `ActionGraph`**，自身只负责 DTO 转换与 disposition 映射，类注释标明"control-plane HTTP 适配；代码内调用请用 `ActionGraph`"。行为不变（既有 control-plane 测试零语义修改通过）。

---

## 3. DX2-C 使用阶梯（文档重构规格）

### 3.1 quick-start.html 重构为五级，每级一个可运行检查点

| 级 | 标题 | 新概念预算 | 检查点 |
|---|---|---|---|
| L0 | Hello Agent（15 分钟） | Action / Condition / Goal（仅 3 个） | `actionGraph.start()` 返回 COMPLETED |
| L1 | 加上门禁 | riskLevel / 人审 / resume | 挂起 → decide → resume 完成 |
| L2 | 自然语言入口 | 解释器（4 行 yml 或规则兜底） | `chat()` 缺参追问 + 补参跑通 |
| L3 | 生产化 | **零新代码概念**（JDBC/脱敏/限额/durability 全 yml） | kill -9 后恢复，副作用恰好一次 |
| L4 | 跨服务 | 事件等待 / 幂等键 | 回调投递恢复 |

- L0 的完整代码必须 ≤ 30 行（一个注解类 + 一行调用）——这是本 PRD 的北极星指标；
- Blackboard / Contribution / Planner 等词**不得出现在 L0**（链接到进阶页即可）。

### 3.2 `docs/learning-path.md`
一页阅读地图：L0–L4 → 打包（Contribution）→ SPI → 运营（console/审批/事件）→ 内部设计（PRD 群）。36 个 frameworkization 文档在此各归一行，并在各自页首加层级徽章（Golden Path / Packaging / SPI / Internal）。

### 3.3 官网
index.html 的 quickstart 区块改为 L0 代码（正门版）；journey 导览不动。

---

## 4. DX2-D dogfood：samples/renewal 迁移

- `RenewalGoals` / `RenewalSeeder` 手写类 → `@ActionGraphGoal` + `@ActionGraphGoalSeeder` 注解形式；
- 其 sample 入口改用 `ActionGraph` 正门；
- 行为与测试语义零变化（允许断言适配）；claims 域不动。

---

## 5. 验收标准（Definition of Done）

1. **北极星**：存在一个集成测试 `HelloAgentGoldenPathTest`——一个注解类（≤3 个 Action + Goal + Seeder）+ 注入**唯一一个** `ActionGraph` Bean + `start()` 断言 COMPLETED；测试源码（除 import）≤ 40 行。
2. `start`：未注册 goalType / 缺必填参数 → `ActionGraphInputException`，消息含可用 goalType 列表或缺失参数名。
3. `chat`：缺参 → `started()==false` + 澄清问题；`chat(input, knownParameters)` 补参后跑通；不抛异常路径有测试。
4. `resume`：审批后经正门恢复完成（与既有 executor.resume 行为等价断言）。
5. Builder：非 Spring 纯 Java 组装正门跑通同一链（core 测试，不依赖 starter）。
6. `ActionGraphRuntimeApiService` 委托后，control-plane 既有测试零语义修改通过。
7. renewal 迁移完成，samples 测试全绿；claims 零改动。
8. 文档：golden-path.md、learning-path.md、quick-start L0–L4 落地；L0 代码 ≤30 行且不含 Blackboard/Contribution 字样（文档守卫测试新增此断言——延续守卫文化）。
9. 规范：新 API `@Experimental(since="0.2.0")`；japicmp、`./gradlew build --rerun-tasks` 全绿；文档一致性守卫通过。

## 6. 实施顺序

A（正门 + Builder + 测试）→ E（RuntimeApiService 委托）→ D（renewal dogfood）→ B/C（文档裁决与阶梯，拿 dogfood 后的真实代码截屏/摘录）。

## 7. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | preview/图导出是否进正门 | 不进；L1 文档教 `ActionGraphValidator`/`ActionGraphExporter` 直用 |
| 2 | start 的 Blackboard 预填充口子 | 不开；有此需求说明该用 SPI 层，文档指路 |
| 3 | chat 多轮会话状态托管 | 不做；knownParameters 由调用方累积（无状态正门） |
| 4 | 旧方式何时标记 @Deprecated | 1.0 后凭采用数据决定，本期绝不 |
