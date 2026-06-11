# PRD — DX1 开发体验与扩展点完备化（链路校验 / LLM 接入骨架 / 重试超时 / 链路打包 / 图导出）

> 交付对象：实现方（Codex）
> 前置版本：当前 main（10 模块，357 tests，japicmp 公共 API 快照已生效）
> 动机：框架的"运行时正确性"已达标，但"开发时确定性"是毛坯——接入方从写完代码到确信能跑之间，缺校验、预演、骨架与打包。F1 试点接入方会在第一周撞上 P0 三项。
> 状态：可直接实施

---

## 0. 一句话目标

让接入方做到：**注册完链路启动即知通不通；接自有 LLM 改配置即可；下游抖动不再误触发冲正；一个业务域一个类整建制注册；随时把 Action 图画出来给评审看。**

## 0.1 总裁决：所有 DX1 新公共 API 以 `@Experimental` 进入

本期全部新公共类型/方法标注 `@Experimental(since = "<本期版本>")`，**不进入 japicmp 冻结承诺**；稳定一个 minor 版本后按 `public-api-compatibility.md` 流程晋升。这是 API 分级机制建立后的第一次实战使用。

## 0.2 兼容性硬约束

- 现有 357 个测试全绿（允许因新增字段微调断言，不允许语义改变）。
- `PolicyDecision`、`RunStatus` 枚举不加值。
- 核心既有类型的 additive 变更仅限 §8 清单。

---

## 1. 范围

### 1.1 In scope
- DX1-A 链路启动期校验 + 计划预演（最高优先）
- DX1-B LLM 接入骨架（OpenAI 兼容客户端 + 模板基类，DeepSeek 降为预设）
- DX1-C Action 级重试与超时
- DX1-D 链路打包注册 SPI（`ActionGraphContribution`）
- DX1-E Action 图导出（Mermaid / DOT）
- 扩展点手册 `docs/frameworkization/extension-points.md`（接自有 LLM / 打包链路 / 重试补偿语义三章）
- starter 自动装配与配置项；samples 双域示范改造（renewal 用 Contribution + 校验，claims 不动——仍冻结）

### 1.2 Out of scope
- 等待外部事件的通用挂起（异步核心回调）——F2 单独立项
- console 图形化展示（本期只产出文本格式导出）
- LLM 流式输出
- 重试的持久化记账（跨重启续重试）——本期重试仅在单次运行进程内

---

## 2. DX1-A 链路校验与计划预演

### 2.1 声明式种子条件（additive）
```java
// GoalDefinition 增加第 5 个组件（保留旧 4 参构造器，默认 Set.of()）
public record GoalDefinition(
        GoalType type, String description, Goal goal,
        List<GoalParameterDefinition> parameters,
        Set<Condition> seedConditions          // 新增：该 Goal 的声明式初始条件
)
```

**裁决 A1：`seedConditions` 是静态声明，不是运行时来源。** 运行时初始条件仍由 `GoalBlackboardSeeder` 产生；本字段只用于校验与预演。声明与 Seeder 的漂移风险用测试助手覆盖（§2.4），**不做运行时强制**。

### 2.2 校验器（core，新包 `com.actiongraph.validation`，纯函数零依赖）
```java
public final class ActionGraphValidator {
    public ValidationReport validate(GoalCatalog catalog, Collection<Action> actions);
}
public record ValidationReport(boolean valid, List<GoalValidation> goals) {}
public record GoalValidation(
        GoalType type, boolean reachable,
        List<PlanStep> previewPlan,            // 可达时给出预演计划
        Set<Condition> missingConditions,      // 不可达时：缺口诊断（见 2.3）
        List<ActionId> danglingActions          // precondition 永不可满足的 Action
) {}
```

### 2.3 诊断质量要求（这是本项的灵魂，不许只报 true/false）
不可达时必须回答"差什么"：
- **缺口条件**：从 seedConditions 做前向闭包（所有可达 condition 集合），goal target 中不在闭包内的 → `missingConditions`；
- **悬空 Action**：precondition 含闭包外条件的 Action → `danglingActions`（典型：condition 字符串拼错/命名空间漏写）；
- 报告文本须含修复提示，例如：`goal 'requestOrderCancellation' 不可达：缺少条件 order:ELIGIBILITY_CHECKED；最接近的已注册 effect 为 order:CANCELLATION_ELIGIBILITY_CHECKED（疑似拼写不一致）`。"最接近"用编辑距离即可，不必复杂。

### 2.4 测试助手（core 的 testFixtures 或独立 test-support 包内类）
```java
// 校验 Seeder 产物覆盖声明（消解裁决 A1 的漂移风险）
SeederConformance.assertSeedsDeclaredConditions(seeder, sampleParameters, goalDefinition);
```

### 2.5 starter 集成
- 启动时自动执行校验，配置 `actiongraph.validation.mode = FAIL | WARN | OFF`，**默认 FAIL**（启动失败并打印 §2.3 诊断）；
- `WARN` 输出到 SLF4J warn。

---

## 3. DX1-B LLM 接入骨架

### 3.1 模板基类（模块 `actiongraph-llm-deepseek`，包 `com.actiongraph.llm`）

**裁决 B1：放进现有 llm 模块，不新建模块**（模块治理默认合并）；模块名与内容物的错位记入治理台账，留待下一 major 统一评估是否拆出通用 LLM artifact。

```java
public abstract class AbstractHttpChatClient implements LlmClient {
    // 框架负责：HTTP 调用、错误归类（LlmClientException）、响应非空校验
    protected abstract HttpRequestSpec buildRequest(LlmRequest request);
    protected abstract String parseContent(String responseBody);
    protected Map<String, String> authHeaders() { /* 默认 Authorization: Bearer */ }
}
```

### 3.2 OpenAI 兼容客户端（90% 行内场景零代码）
```java
public final class OpenAiCompatibleChatClient extends AbstractHttpChatClient {
    // 构造参数：baseUrl, model, apiKey, extraHeaders, timeout
    // 请求体: {model, messages[system,user], response_format:{type:json_object}, max_tokens, stream:false}
    // 响应取 choices[0].message.content
}
```
`DeepSeekChatClient` 重构为继承该骨架的薄预设（保留类名与 `fromEnvironment()`，公共行为不变——现有 LLM 测试不改语义通过）。

### 3.3 starter 配置
```yaml
actiongraph:
  llm:
    provider: openai-compatible      # 或 deepseek / none（默认 none）
    base-url: https://llm-gateway.bank.internal/v1/chat/completions
    model: qwen-max
    api-key-env: BANK_LLM_KEY        # 只允许环境变量名，不允许直写密钥
    timeout: 20s
    headers:                          # 行内网关常见的额外鉴权头
      X-Gateway-AppId: actiongraph
```
**裁决 B2：配置项只接受密钥的环境变量名**（`api-key-env`），杜绝 application.yml 里出现明文密钥。

---

## 4. DX1-C Action 重试与超时

### 4.1 模型（core，`com.actiongraph.action`）
```java
public record ActionExecutionPolicy(
        int maxAttempts,            // 含首次；默认 1 = 不重试（保持现语义）
        Duration backoff,           // 固定退避；默认 0
        Duration timeout            // 单次尝试超时；默认 null = 不限时
) {
    public static ActionExecutionPolicy none();
}

// Action 接口 additive：
default ActionExecutionPolicy executionPolicy() { return ActionExecutionPolicy.none(); }
```
注解同步增加属性：`@ActionGraphAction(..., maxAttempts = 3, backoffMillis = 500, timeoutMillis = 5000)`。

### 4.2 裁决 C1：重试仅限显式声明，且声明即承诺幂等
`maxAttempts > 1` 的 Action 视为**作者承诺 execute 幂等**（重复执行不产生重复副作用）。框架不验证幂等，但扩展点手册必须用黑体写明这个契约。默认 1，所有现有 Action 行为不变。

### 4.3 裁决 C2：超时 = 结果未知，按"已执行"参与补偿
单次尝试超时后：
- 框架**不假设业务调用失败**（服务端可能已成功）；
- 该 Action **压入补偿栈后**再走失败路径——它的 `compensate` 会被调用；
- 因此扩展点手册必须写明：**补偿实现必须容忍"正向操作可能从未发生"**（如 voidDraft 前先查存在性——现有示例已是此风格）；
- trace 记 `ACTION_TIMED_OUT`（detail 注明 outcome=UNKNOWN），随后按既有失败补偿流程走。

### 4.4 裁决 C3：超时实现与中断语义
- 执行包装在独立（虚拟）线程，主循环 `Future.get(timeout)`；
- 超时后调用 `cancel(true)` 尝试中断，但**无论中断是否生效都按 C2 处理**——不等待僵尸调用返回；
- 重试与超时的交互：超时的尝试**不再重试**（结果未知时重试需要幂等性之外还需要去重凭证，超出本期）。即 timeout 触发 → 直接进入 C2 流程，剩余 attempts 作废。

### 4.5 重试流程与 trace
- 失败（异常/`ActionResult.fail`）且未达 maxAttempts → 记 `ACTION_RETRIED`（attempt 序号、backoff）→ 退避后重试；
- 重试耗尽 → 既有失败补偿路径；
- `TraceEventType` 新增 `ACTION_RETRIED`、`ACTION_TIMED_OUT` 两值（枚举加值对 japicmp 为兼容变更）。

### 4.6 配置覆盖（裁决 C4：配置 > 代码声明 > 默认）
```yaml
actiongraph:
  execution:
    policies:
      - action-id: "order.lookup"
        max-attempts: 3
        backoff: 500ms
        timeout: 5s
```
运维可不改代码调参；按 actionId 精确匹配，无通配。

---

## 5. DX1-D 链路打包注册 SPI

### 5.1 接口（core，新包 `com.actiongraph.contribution`，零 Spring）
```java
public interface ActionGraphContribution {
    default List<Action> actions() { return List.of(); }
    default List<GoalDefinition> goals() { return List.of(); }
    default List<GoalBlackboardSeeder> seeders() { return List.of(); }
    default List<Object> annotatedBeans() { return List.of(); }  // 交给 AnnotatedActionFactory
}
```

### 5.2 starter 行为
- 自动发现所有 `ActionGraphContribution` Bean，合并注册进 `ActionRegistry` / `GoalCatalog` / `GoalBlackboardSeederRegistry`；
- **重复 actionId / goalType 跨 Contribution 冲突 → 启动失败**（报出两个来源类名）；
- 注册完成后自动触发 DX1-A 校验（顺序保证：先聚合后校验）。

### 5.3 示范改造
`samples` 的 renewal 域改为一个 `RenewalContribution` 整建制注册（行为不变，现有测试全绿）；claims 域**不动**（仍处冻结）。

---

## 6. DX1-E Action 图导出

### 6.1 接口（core，新包 `com.actiongraph.graph`，纯字符串拼装零依赖）
```java
public final class ActionGraphExporter {
    public String toMermaid(Collection<Action> actions);                 // 全图
    public String toMermaid(Collection<Action> actions, Goal goal);      // 高亮目标条件 + 可达子图
    public String toDot(Collection<Action> actions);                     // Graphviz
}
```

### 6.2 图语义
- 节点两类：condition（圆角）与 action（方框，标注 riskLevel；requiresHumanReview 的加标记）；
- 边：condition →(precondition)→ action →(effect)→ condition；
- 带 Goal 的版本：目标条件高亮，从 seedConditions 不可达的部分置灰——**与 DX1-A 的闭包计算共用同一段逻辑**（实现提示：闭包算法放 validation 包，graph 包复用）。

### 6.3 用途定位（写进手册）
开发自查 + **评审材料**：给业务方/安全评审展示"AI 只能在这张图内行走"。Mermaid 块可直接贴进行内 wiki/markdown。

---

## 7. 验收标准（Definition of Done）

1. **回归**：现有 357 测试全绿；renewal 改造后行为不变；claims 域零改动。
2. **校验**：注册缺一个 Action 的 renewal 链 → 启动失败，诊断报出缺失 condition 与最接近拼写建议；`mode=WARN/OFF` 行为正确；预演计划与实际执行序列一致（同一 fixture 断言）。
3. **LLM 骨架**：`OpenAiCompatibleChatClient` 对 MockWebServer 完成请求体/鉴权头/响应解析/错误映射测试；DeepSeek 预设保持 `fromEnvironment()` 兼容、现有测试语义不变；yml 中直写 `api-key` 字段 → 启动失败并提示用 `api-key-env`。
4. **重试**：声明 maxAttempts=3 的 Action 前两次抛异常第三次成功 → run COMPLETED，trace 含 2 条 `ACTION_RETRIED`；耗尽 → 既有补偿路径。
5. **超时**：execute 睡眠超过 timeout → `ACTION_TIMED_OUT`、该 Action 的 compensate 被调用（验证 C2）、运行 `FAILED_COMPENSATED`；超时后不重试（验证 C3）。
6. **Contribution**：两个 Contribution 注册重复 actionId → 启动失败并报出两个来源；renewal 经 Contribution 注册后端到端通过。
7. **图导出**：renewal 全图 Mermaid 输出可被 mermaid 解析（语法冒烟：节点/边数断言）；带 Goal 版本高亮目标条件。
8. **API 分级**：所有新公共类型带 `@Experimental`；japicmp 快照更新走既有流程；`./gradlew build --rerun-tasks` 全绿。
9. **手册**：`extension-points.md` 三章齐备，重试幂等契约与补偿容忍语义（C1/C2）以醒目方式写明。

## 8. 核心 additive 变更清单（仅限以下，不得再多）

1. `GoalDefinition` + `seedConditions`（兼容构造器）；
2. `Action` + `default executionPolicy()`；
3. `@ActionGraphAction` + `maxAttempts/backoffMillis/timeoutMillis` 属性（默认值=现行为）；
4. `TraceEventType` + `ACTION_RETRIED`、`ACTION_TIMED_OUT`；
5. 新包：`com.actiongraph.validation`、`com.actiongraph.contribution`、`com.actiongraph.graph`（全部 `@Experimental`）。

## 9. 实施顺序

A（校验+闭包算法）→ E（图导出，复用闭包）→ D（Contribution，接上自动校验）→ C（重试超时）→ B（LLM 骨架，与前四项无依赖可并行）。

## 10. 开放问题（有默认值，不阻塞）

| # | 问题 | 默认 |
|---|---|---|
| 1 | 校验是否考虑 runtimeGuard（规划期不可知） | 不考虑，与 planner 同语义（裁决 C of PRD-v0） |
| 2 | 退避是否支持指数 | 本期固定退避；字段名留 `backoff` 不带 fixed 字样，指数留扩展 |
| 3 | 图导出是否进 console 端点 | 本期不进；console 侧 F2 再议 |
| 4 | `annotatedBeans()` 与 Spring 自动扫描的关系 | 并存；Contribution 显式供给优先级高，同 id 冲突按 §5.2 失败 |
