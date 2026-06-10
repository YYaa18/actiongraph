# ActionGraph

ActionGraph 是一个面向 Java 企业业务系统的类型化 GOAP 框架，用来把自然语言目标转成安全、可审计、可补偿的业务 Action 执行链。

它的核心原则很简单：**LLM 负责理解用户目标，确定性规划器负责组合路径，业务系统拥有最终裁决权。** LLM 不生成 Plan，不直接操作数据库，也不能绕过权限、审批和补偿边界。

## 为什么需要 ActionGraph

企业系统接入 AI 时，真正的难点不是“能不能回答问题”，而是“敢不敢让 AI 执行业务动作”。

常见方案的问题：

- 让 LLM 直接调 API 或写 SQL：不可控、不可复现、不可审计。
- 只做问答机器人：能解释制度，但不能把流程办完。
- 各业务系统各自接 AI：prompt、审批、审计、回滚重复建设，标准不统一。

ActionGraph 提供的是一个统一的 AI 执行底座：业务系统只暴露可控的 Action，框架负责规划、审批、执行、恢复、补偿和审计。

## 核心能力

- **确定性 GOAP 规划**：只基于 Action 的 precondition / effect 搜索路径，同样输入得到同样计划。
- **类型化 Blackboard**：运行态数据按 Java 类型和 key 管理，避免字符串拼装式传参。
- **每步后重规划**：执行完一步就重新规划，运行时事实变化可自动改道。
- **Runtime Guard**：依赖真实业务值的校验放到执行前判断，不污染规划器。
- **多级人工审批挂起 / 恢复**：高风险 Action 可按审批链逐级挂起，审批通过后从同一个 runId 原子认领恢复，避免重复副作用。
- **Saga 补偿**：失败或拒绝时按已执行 Action 逆序补偿，避免残留孤儿副作用。
- **全链路审计**：目标解释、计划生成、Action 执行、Blackboard 更新、审批、补偿全部可追踪。
- **敏感数据脱敏**：可对 Trace 与审批预览中的身份证、银行卡、手机号、邮箱和指定字段做脱敏；挂起快照保持无损恢复。
- **审计链防篡改**：TraceEvent 按 runId 形成链式 SHA-256 哈希，支持验证落库留痕是否被改写。
- **单笔额度策略**：业务方提供金额提取器后，可按 action / 币种配置硬限额拒绝和超阈值升级审批。
- **Spring Boot Starter**：业务类无需实现框架接口，通过注解即可注册 Action。
- **LLM Goal Interpreter**：支持 DeepSeek 兼容接口，LLM 故障可降级到规则解释器。
- **JDBC 持久化**：支持 Trace、挂起运行、审批任务、结构化记忆的持久化仓储，Trace 批量写入降低运行路径开销。

## 模块结构

| 模块 | 作用 |
|---|---|
| `actiongraph-core` | 核心 Action、Planning、Runtime、Policy、Trace、Memory、Interpretation API |
| `actiongraph-llm-deepseek` | DeepSeek 兼容 LLM 客户端与 GoalCatalog prompt 支持 |
| `actiongraph-persistence-jdbc` | Trace、挂起运行、审批任务、记忆的 JDBC 仓储 |
| `actiongraph-spring-boot-starter` | Spring Boot 自动装配与注解扫描 |
| `actiongraph-samples` | 续约报价、订单取消、理赔资料预审三个完整参考域 |

## 快速接入

Spring Boot 应用通常引入：

```kotlin
dependencies {
    implementation("com.actiongraph:actiongraph-spring-boot-starter:0.1.0")
    implementation("com.actiongraph:actiongraph-llm-deepseek:0.1.0")
    implementation("com.actiongraph:actiongraph-persistence-jdbc:0.1.0")
}
```

配置前缀为 `actiongraph.*`：

```yaml
actiongraph:
  planner:
    max-depth: 32
    max-expansions: 10000
  executor:
    max-steps: 64
  actions:
    auto-register-annotated: true
  masking:
    enabled: false
    blocked-keys: [idCard, cardNo, customerName]
```

把已有业务 Service 暴露为 Action：

```java
@ActionGraphAction(
        id = "order.lookup",
        preconditions = "order-cancellation:ORDER_ID_PRESENT",
        effects = "order-cancellation:ORDER_LOADED"
)
public OrderRecord lookup(OrderId orderId) {
    return orderService.find(orderId);
}

@ActionGraphGuard(actionId = "order.cancellation.request.draft")
public boolean canDraft(CancellationEligibility eligibility) {
    return eligibility.eligible();
}

@ActionGraphCompensation(actionId = "order.cancellation.request.draft")
public void voidDraft(CancellationRequestDraft draft) {
    cancellationRequestService.voidDraft(draft.requestId());
}
```

## 运行示例

构建并运行测试：

```bash
./gradlew build
```

运行续约报价样例：

```bash
./gradlew :actiongraph-samples:run --args="--approve-human-review 帮客户 C123 准备一份续约报价"
```

运行订单取消样例：

```bash
./gradlew :actiongraph-samples:runOrderCancellationSample --args="--approve-human-review Cancel order O100"
```

运行理赔资料预审样例：

```bash
./gradlew :actiongraph-samples:runClaimsPrecheckSample --args="--approve-human-review 帮我预审理赔 CLM100 并准备赔付申请"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics
```

## 设计边界

ActionGraph 明确避免把企业系统交给 LLM 自由发挥：

- LLM 不生成 Plan。
- Planner 不调用业务代码，只做符号搜索。
- Runtime Guard 不进入 Planner，只在执行前判断真实业务值。
- 高风险 Action 默认走人工审批。
- 终态失败或拒绝触发逆序补偿。
- 所有关键事件写入 Trace。

## 当前成熟度

- 133 个自动化测试通过。
- 并发冒烟约 6000 runs/s；重复 resume 只产生一次业务副作用。
- 5 个 Gradle 模块完成拆分。
- 3 个参考业务域完整跑通。
- 支持模块发布到 Maven Local / 私服。
- 支持 Spring Boot 注解式接入。
- 支持 suspend / resume、JDBC persistence、human review、structured memory。
- F0 内核金融化完成：Trace/审批预览敏感数据脱敏、审计链防篡改、多级审批链、单笔额度策略。
- F1 已进入场景打穿：理赔资料预审 + 赔付申请草稿样板域已跑通，并输出批量拦截率、审计完整率、平均耗时指标。

## 文档

- [快速接入指南](docs/quick-start.html)
- [技术汇报演示文档](docs/actiongraph-pitch.html)
- [框架化笔记](docs/frameworkization/)
- [原始 PRD](docs/PRD-v0.md)
- [F0 金融化 PRD](docs/PRD-F0-finance.md)
- [F1 理赔预审样板域笔记](docs/f1-claims-precheck-notes.md)

## 适合的场景

ActionGraph 特别适合高频、规则明确、需要审计和审批的企业业务流程，例如：

- 合同续约报价
- 理赔资料预审
- 订单取消申请
- 退款 / 调账草稿
- 内部审批发起
- 跨系统资料补齐
- 风险动作执行前预审

它不适合让 LLM 自由探索、自由执行、无审计地修改正式数据的场景。
