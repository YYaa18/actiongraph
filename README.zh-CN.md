# ActionGraph

[![CI](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml/badge.svg)](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml)

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
- **Spring Boot Starter**：业务类无需实现框架接口，通过注解即可注册 Action，并自动装配运行时默认 Bean。
- **JDBC Spring Boot Starter**：生产持久化作为可选组件独立引入，按配置自动替换 Trace、挂起、审批、记忆仓储。
- **Human Review Spring Boot Starter**：审批回调端点作为可选生态组件独立引入，供外部审批系统回写决策。
- **Console Core**：只读控制台查询服务与响应模型独立成无 Web 组件，可供自研控制台、CLI 或网关适配复用。
- **Console Spring Boot Starter**：控制层/监管台作为可选生态组件独立引入，提供只读 Console 页面和查询端点。
- **LLM Goal Interpreter**：支持 DeepSeek 兼容接口，LLM 故障可降级到规则解释器。
- **JDBC 持久化**：支持 Trace、挂起运行、审批任务、结构化记忆的持久化仓储，Trace 批量写入降低运行路径开销，挂起快照恢复支持 Blackboard 类型白名单和格式版本校验。
- **只读 Console Read Model**：JDBC Trace 表可分页/筛选查询运行摘要、Trace 详情、终态/挂起状态和审计链验证结果，支撑后续服务化监管视图。

## 模块结构

| 模块 | 作用 |
|---|---|
| `actiongraph-bom` | Maven / Gradle BOM，用于统一 ActionGraph 各组件版本 |
| `actiongraph-core` | 核心 Action、Planning、Runtime、Policy、Trace、Memory、Interpretation API |
| `actiongraph-llm-deepseek` | DeepSeek 兼容 LLM 客户端与 GoalCatalog prompt 支持 |
| `actiongraph-persistence-jdbc` | Trace、挂起运行、审批任务、记忆的 JDBC 仓储 |
| `actiongraph-spring-boot-starter` | Spring Boot 自动装配与注解扫描 |
| `actiongraph-jdbc-spring-boot-starter` | 可选 JDBC 仓储 Spring Boot 自动装配 |
| `actiongraph-human-review-spring-boot-starter` | 可选审批回调端点，供外部审批系统回写决策 |
| `actiongraph-console-core` | 可复用只读 Console 查询服务与响应模型 |
| `actiongraph-console-spring-boot-starter` | 可选只读 Console 页面与 Spring MVC 查询端点 |
| `actiongraph-samples` | 续约报价、订单取消、理赔资料预审三个完整参考域 |

## 快速接入

Spring Boot 应用通常引入：

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))

    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    // 可选生态 / 控制层组件：
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
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
  persistence:
    jdbc:
      enabled: true
      suspended-run-claim-timeout: 15m
      blackboard:
        allowed-packages:
          - com.example.business
  human-review:
    callback-endpoint:
      enabled: true
      path: /actiongraph/human-review/callbacks
      token-header: X-ActionGraph-Review-Token
      shared-secret: ${ACTIONGRAPH_REVIEW_CALLBACK_SECRET}
  console:
    enabled: true
    path: /actiongraph/console
    token-header: X-ActionGraph-Console-Token
    shared-secret: ${ACTIONGRAPH_CONSOLE_SECRET}
    default-limit: 50
    max-limit: 200
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

引入 `actiongraph-jdbc-spring-boot-starter` 并启用 `actiongraph.persistence.jdbc.enabled=true` 后，Spring Boot 应用只要提供 `DataSource`，Trace、挂起运行、审批任务、结构化记忆和 Console read model 都会自动切到 JDBC 实现。非 Spring 服务或需要完全手工控制的服务，仍然可以直接使用底层 `actiongraph-persistence-jdbc`。

引入 `actiongraph-human-review-spring-boot-starter` 并启用 `actiongraph.human-review.callback-endpoint.enabled=true` 后，审批系统回调可直接 POST 到配置的端点。该端点需要应用中存在 `HumanReviewRepository` Bean；`actiongraph-spring-boot-starter` 会提供内存默认实现，启用 JDBC starter 后会使用持久化实现。

配置 `shared-secret` 后，请求必须携带对应 Header；缺失或错误会返回 `401 UNAUTHORIZED`。生产环境建议通过环境变量或密钥系统注入该值，不要写死在仓库配置文件中。

```json
{
  "runId": "RUN-1",
  "actionId": "claim.approval.request",
  "expectedStageIndex": 0,
  "decision": "APPROVED",
  "reviewer": "claims-checker",
  "comment": "approved"
}
```

自研控制台、CLI 或企业网关适配可以直接依赖 `actiongraph-console-core`，复用只读查询服务和 JSON 响应模型，不引入 Spring MVC endpoint。引入 `actiongraph-console-spring-boot-starter` 并启用 `actiongraph.console.enabled=true` 后，如果应用提供 `DataSource`，Console starter 会暴露只读运行监控接口：

```text
GET /actiongraph/console
GET /actiongraph/console/runs?limit=50&offset=0&status=COMPLETED&auditComplete=true
GET /actiongraph/console/runs/{runId}
GET /actiongraph/console/runs/{runId}/trace
```

内置页面包含运行列表、筛选栏、选中运行元数据和 Trace 时间线。API 返回内容包含分页元数据、run 状态、首尾 Trace 时间、Trace 事件列表、Trace 事件数、审计链是否完整以及首个断链序号。配置 `actiongraph.console.shared-secret` 后，API 访问方必须携带 Console token Header。

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
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-001 --environment local"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-REVIEW-001 --environment local --review-mode suspend-resume --simulate-review-wait-ms 5"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --review-decisions actiongraph-samples/src/main/resources/claims-precheck-review-decisions.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-EXTERNAL-REVIEWS --environment local"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --review-callbacks actiongraph-samples/src/main/resources/claims-precheck-review-callbacks.jsonl --review-callback-secret review-secret --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-CALLBACKS --environment local"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args='--jdbc-url jdbc:postgresql://db.example/claims --jdbc-user actiongraph_reader --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-JDBC-001 --environment staging'
```

JDBC 批量输入使用标准 `DriverManager`；连接真实数据库前，需要把目标数据库驱动加入样例运行 classpath。脱敏视图契约见 `actiongraph-samples/src/main/resources/sql/claims-precheck-source-contract.sql`，PostgreSQL 方言映射见 `actiongraph-samples/src/main/resources/sql/postgresql/claims-precheck-source-contract.sql` 和 [Claims Precheck PostgreSQL Mapping](docs/frameworkization/claims-precheck-postgresql.md)。
批量报告会输出 Markdown、CSV 和只读 HTML console，按样本拆出总耗时、业务 Action 耗时、框架调度耗时和审批等待耗时；`suspend-resume` / `external-decisions` / `external-callbacks` 审批模式会走真实挂起/恢复路径，并从审批任务时间戳计算等待。`external-callbacks` 会把 JSONL 审批回调投递交给 `HumanReviewCallbackHandler`，覆盖共享密钥校验和重复投递幂等。生产审批系统可以通过 `HumanReviewCallbackHandler` 写入审批结果，也可以启用 Spring Boot 回调端点接收 HTTP 回调。

## 设计边界

ActionGraph 明确避免把企业系统交给 LLM 自由发挥：

- LLM 不生成 Plan。
- Planner 不调用业务代码，只做符号搜索。
- Runtime Guard 不进入 Planner，只在执行前判断真实业务值。
- 高风险 Action 默认走人工审批。
- 终态失败或拒绝触发逆序补偿。
- 所有关键事件写入 Trace。

## 当前成熟度

- 191 个自动化测试通过。
- 并发冒烟约 6000 runs/s；重复 resume 只产生一次业务副作用。
- 10 个 Gradle 模块完成拆分。
- 3 个参考业务域完整跑通。
- 支持模块发布到 Maven Local / 私服。
- 支持 Spring Boot 注解式接入。
- 支持 suspend / resume、JDBC persistence、human review、structured memory。
- F0 内核金融化完成：Trace/审批预览敏感数据脱敏、审计链防篡改、多级审批链、单笔额度策略。
- F1 已进入场景打穿：理赔资料预审 + 赔付申请草稿样板域已跑通，可从 CSV 或 JDBC 样本、PostgreSQL 脱敏视图映射、外部审批决策或 JSONL 回调输出带批次、环境、样本来源、限额参数、审批模式和耗时拆分的 Markdown/CSV/HTML 指标报告。

## 文档

- [快速接入指南](docs/quick-start.html)
- [真实 LLM 冒烟测试](docs/frameworkization/llm-smoke.md)
- [人工审批集成](docs/frameworkization/human-review.md)
- [理赔预审 PostgreSQL 映射](docs/frameworkization/claims-precheck-postgresql.md)
- [理赔预审审批回调重放](docs/frameworkization/claims-precheck-review-callbacks.md)
- [理赔预审只读 Console](docs/frameworkization/claims-precheck-console.md)
- [依赖组合指南](docs/frameworkization/dependency-composition.md)
- [生态组件模块化](docs/frameworkization/ecosystem-modularity.md)
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
