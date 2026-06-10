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
- **治理策略库**：脱敏、额度、规则权限/租户校验可作为非 Spring 组件独立引入。
- **Human Review 治理扩展**：额度审批属性和风险审批链路由独立成可选组件，只有需要人审增强时才引入。
- **注解适配器**：普通 Java 方法可通过注解注册成 Action，非 Spring 服务也可独立使用。
- **Spring Boot Starter**：业务类无需实现框架接口，通过注解即可注册 Action，只自动装配基础运行时 Bean。
- **Governance Spring Boot Starter**：脱敏、额度策略和规则权限作为可选治理组件独立引入。
- **Governance Human Review Spring Boot Starter**：额度审批属性和多级审批路由作为可选人审治理组件独立引入。
- **JDBC Spring Boot Starter**：核心生产持久化作为可选组件独立引入，按配置替换 Trace 与挂起仓储；审批和记忆的 JDBC 持久化由独立组件按需引入。
- **Human Review 组件**：审批任务仓储、审批链、回调处理和 repository-backed policy 独立成非 Spring 组件，可由审批服务、批处理或业务服务按需复用。
- **Human Review API**：审批任务查询与决策写入服务独立成纯 Java 组件，审批门户、CLI、企业网关可直接复用。
- **Human Review Spring Boot Starter**：仓储型审批策略作为可选 Spring 组件独立引入。
- **Human Review API Spring Boot Starter**：审批任务列表、运行维度查询、详情与决策 HTTP 端点独立成可选控制层组件。
- **Human Review Callback Spring Boot Starter**：审批回调 HTTP 端点作为可选控制层组件独立引入，供外部审批系统回写决策。
- **Console Core**：只读控制台查询服务与响应模型独立成无 Web 组件，可供自研控制台、CLI 或网关适配复用。
- **Console JDBC Adapter**：JDBC Trace read model 作为控制层适配器独立引入，Console Core 不绑定具体存储。
- **Console Export**：运行摘要 CSV、Trace CSV/JSONL 导出独立成纯 Java 组件，批处理、CLI、审计归档可直接复用。
- **Console JDBC Spring Boot Starter**：JDBC Console 仓储自动装配独立成可选组件，控制层是否使用 JDBC 可单独选择。
- **Console API Spring Boot Starter**：只读 Console JSON 查询端点独立成可选控制层组件，可只暴露 API 给企业网关或自研前端。
- **Console UI Spring Boot Starter**：内置 Console 页面独立成可选控制层组件，可与内置 API 或自研 API 组合。
- **Console Export Spring Boot Starter**：CSV/JSONL 审计导出 HTTP 端点独立成可选控制层组件，可只暴露导出能力。
- **Console Spring Boot Starter**：兼容聚合组件，同时引入 Console API 与 UI，老接入方式不破坏。
- **结构化记忆组件**：Memory 记录、仓储接口、内存实现与 Blackboard 上下文加载器独立成可选组件，core-only 服务无需引入。
- **Memory Spring Boot Starter**：结构化记忆的 Spring 默认装配独立成可选组件，需要时再引入。
- **目标解释契约组件**：GoalCatalog、GoalInterpreter、解释结果和 Blackboard seeder 独立成可选入口层组件，规则解释器也可脱离 LLM 使用。
- **Runtime API**：目标解释、启动运行和恢复运行入口服务独立成纯 Java 组件，企业网关、CLI 或自研控制器可直接复用。
- **Runtime API Spring Boot Starter**：解释目标、启动运行和恢复挂起运行的 HTTP 端点独立成可选控制层组件。
- **LLM Goal Interpreter**：通用解释器、GoalCatalog prompt 渲染和结构化输出解析独立成组件；DeepSeek 兼容客户端作为可选 provider 引入，LLM 故障可降级到规则解释器。
- **JDBC 持久化**：核心模块支持 Trace、挂起运行和 Trace read model；审批任务与结构化记忆分别有独立 JDBC 组件，Trace 批量写入降低运行路径开销，挂起快照恢复支持 Blackboard 类型白名单和格式版本校验。
- **只读 Console Read Model**：JDBC Trace 表可分页/筛选查询运行摘要、Trace 详情、终态/挂起状态和审计链验证结果，支撑后续服务化监管视图。

## 模块结构

| 模块 | 作用 |
|---|---|
| `actiongraph-bom` | Maven / Gradle BOM，用于统一 ActionGraph 各组件版本 |
| `actiongraph-core` | 核心 Action、Planning、Runtime、Policy、Trace API |
| `actiongraph-annotations` | 可选纯 Java 注解与适配器，将普通方法注册为 Action |
| `actiongraph-memory` | 可选结构化记忆记录、仓储接口、内存实现与 Blackboard 上下文加载器 |
| `actiongraph-memory-spring-boot-starter` | 可选结构化记忆 Spring Boot 自动装配 |
| `actiongraph-interpretation` | 可选目标解释契约、GoalCatalog 元数据与 Blackboard seeder |
| `actiongraph-runtime-api` | 可选目标解释、启动运行与恢复运行入口服务 |
| `actiongraph-human-review` | 可选审批任务仓储、回调处理器、审批链与 repository-backed policy |
| `actiongraph-human-review-api` | 可选审批任务查询与决策服务 |
| `actiongraph-governance` | 可选非 Spring 治理策略：脱敏、额度、规则权限/租户校验 |
| `actiongraph-governance-human-review` | 可选非 Spring 人审治理扩展：额度审批属性与风险审批链路由 |
| `actiongraph-llm` | 通用 LLM 目标解释、GoalCatalog prompt 渲染与结构化输出解析 |
| `actiongraph-llm-deepseek` | 可选 DeepSeek 兼容 LLM 客户端，会传递引入 `actiongraph-llm` |
| `actiongraph-persistence-jdbc` | Trace、挂起运行与 Trace read model 的核心 JDBC 仓储 |
| `actiongraph-memory-jdbc` | 可选结构化记忆 JDBC 仓储 |
| `actiongraph-human-review-jdbc` | 可选审批任务 JDBC 仓储 |
| `actiongraph-spring-boot-starter` | Spring Boot 自动装配与注解扫描，会传递引入 `actiongraph-annotations` |
| `actiongraph-governance-spring-boot-starter` | 可选治理策略自动装配：脱敏、额度、规则权限 |
| `actiongraph-governance-human-review-spring-boot-starter` | 可选人审治理自动装配：额度审批属性与多级审批路由 |
| `actiongraph-jdbc-spring-boot-starter` | 可选核心 JDBC 仓储 Spring Boot 自动装配 |
| `actiongraph-memory-jdbc-spring-boot-starter` | 可选结构化记忆 JDBC 仓储自动装配 |
| `actiongraph-human-review-jdbc-spring-boot-starter` | 可选审批任务 JDBC 仓储自动装配 |
| `actiongraph-runtime-api-spring-boot-starter` | 可选 Spring MVC 运行入口端点 |
| `actiongraph-human-review-spring-boot-starter` | 可选仓储型审批策略自动装配 |
| `actiongraph-human-review-api-spring-boot-starter` | 可选 Spring MVC 审批任务查询与决策端点 |
| `actiongraph-human-review-callback-spring-boot-starter` | 可选 Spring MVC 审批回调 HTTP 端点，供外部审批系统回写决策 |
| `actiongraph-console-core` | 可复用只读 Console 查询服务与响应模型 |
| `actiongraph-console-jdbc` | 可选 Console JDBC read model 适配器 |
| `actiongraph-console-export` | 可复用 Console CSV/JSONL 审计导出服务 |
| `actiongraph-console-spring-boot-autoconfigure` | 可选 Console Spring 公共自动装配，供 API / UI / Export starter 复用 |
| `actiongraph-console-api-spring-boot-starter` | 可选只读 Console Spring MVC JSON 查询端点 |
| `actiongraph-console-ui-spring-boot-starter` | 可选内置 Console Spring MVC 页面 |
| `actiongraph-console-export-spring-boot-starter` | 可选 Console Spring MVC CSV/JSONL 审计导出端点 |
| `actiongraph-console-jdbc-spring-boot-starter` | 可选 Console JDBC 仓储自动装配 |
| `actiongraph-console-spring-boot-starter` | 兼容聚合包，同时引入 Console API 与 UI starter |
| `actiongraph-samples` | 续约报价、订单取消、理赔资料预审三个完整参考域 |

## 快速接入

Spring Boot 应用通常引入：

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))

    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-memory-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    // 可选生态 / 控制层组件：
    implementation("com.actiongraph:actiongraph-memory-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-runtime-api-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-api-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-callback-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-api-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-ui-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-export-spring-boot-starter")
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
  runtime:
    api:
      enabled: true
      path: /actiongraph/runtime
      token-header: X-ActionGraph-Runtime-Token
      shared-secret: ${ACTIONGRAPH_RUNTIME_API_SECRET}
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
    api:
      enabled: true
      path: /actiongraph/human-review/tasks
      token-header: X-ActionGraph-Review-Token
      shared-secret: ${ACTIONGRAPH_REVIEW_API_SECRET}
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

引入 `actiongraph-jdbc-spring-boot-starter` 并启用 `actiongraph.persistence.jdbc.enabled=true` 后，Spring Boot 应用只要提供 `DataSource`，Trace、挂起运行和 Trace read model 会切到 JDBC 实现。需要审批任务或结构化记忆也持久化时，再显式引入 `actiongraph-human-review-jdbc-spring-boot-starter` / `actiongraph-memory-jdbc-spring-boot-starter`。非 Spring 服务或需要完全手工控制的服务，可以分别使用底层 `actiongraph-persistence-jdbc`、`actiongraph-human-review-jdbc`、`actiongraph-memory-jdbc`。

非 Spring 服务如果只需要框架内置的脱敏、额度或规则权限/租户校验，可以直接依赖 `actiongraph-governance`，手工组装对应策略，不需要引入 Spring starter。需要额度审批属性或风险审批链路由时，再额外依赖 `actiongraph-governance-human-review`。

非 Spring 服务如果只需要结构化长期记忆，可以直接依赖 `actiongraph-memory`，手工使用 `MemoryRepository` 与 `MemoryContextLoader`，不需要引入 Spring、JDBC 或 LLM 组件。

Spring 服务如果需要结构化长期记忆，可以显式引入 `actiongraph-memory-spring-boot-starter` 获取内存默认实现和 `MemoryContextLoader`；同时启用 `actiongraph-memory-jdbc-spring-boot-starter` 后会自动让位给 JDBC `MemoryRepository`。

非 Spring 服务如果只需要目标目录、规则解释器或解释结果到 Blackboard 的装配桥，可以直接依赖 `actiongraph-interpretation`，不需要引入 LLM provider。

非 Spring 服务如果需要把“解释目标 → 装配 Blackboard → 启动运行 / 恢复运行”作为稳定入口服务，可以直接依赖 `actiongraph-runtime-api`。它只组合 `GoalInterpreter`、`GoalBlackboardSeederRegistry`、`GoapExecutor` 和 `ActionRegistry`，不自带 LLM provider、不创建持久化、不暴露 HTTP。Spring MVC 服务需要入口端点时，再引入 `actiongraph-runtime-api-spring-boot-starter` 并启用 `actiongraph.runtime.api.enabled=true`：

```text
Runtime API starter: POST /actiongraph/runtime/interpret
Runtime API starter: POST /actiongraph/runtime/runs
Runtime API starter: POST /actiongraph/runtime/runs/{runId}/resume
```

非 Spring 服务如果需要审批任务仓储、审批链或审批回调处理，可以直接依赖 `actiongraph-human-review`，不需要引入 Spring MVC endpoint；如果审批门户、CLI 或企业网关需要稳定的任务查询与决策服务，可以再依赖 `actiongraph-human-review-api`。

引入 `actiongraph-governance-spring-boot-starter` 后，`actiongraph.masking.*` 与 `actiongraph.limits.*` 才会生效。需要把额度规则写入审批 attributes，或启用 `actiongraph.human-review.risk-based-approval-chain` 的风险审批链路由时，再引入 `actiongraph-governance-human-review-spring-boot-starter`。只引入基础 Spring starter 时，这些治理能力保持中性默认值：不脱敏、默认权限放行、无额度升级，高风险动作使用安全挂起默认策略。

引入 `actiongraph-human-review-spring-boot-starter` 后，Spring 服务会获得仓储型人工审批默认装配。该 starter 默认提供内存 `HumanReviewRepository`，同时启用 `actiongraph-human-review-jdbc-spring-boot-starter` 后会使用持久化实现。需要给审批门户或企业网关暴露任务列表、运行维度查询、详情和决策端点时，再引入 `actiongraph-human-review-api-spring-boot-starter` 并启用 `actiongraph.human-review.api.enabled=true`；需要接收外部审批系统 HTTP 回调时，再引入 `actiongraph-human-review-callback-spring-boot-starter` 并启用 `actiongraph.human-review.callback-endpoint.enabled=true`。

```text
API starter: GET  /actiongraph/human-review/tasks/pending
API starter: GET  /actiongraph/human-review/tasks/runs/{runId}
API starter: GET  /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}
API starter: POST /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}/decision
Callback starter: POST /actiongraph/human-review/callbacks
```

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

自研控制台、CLI 或企业网关适配可以直接依赖 `actiongraph-console-core`，复用只读查询服务、JSON 响应模型和 `ConsoleRunRepository` 端口，不引入 Spring MVC endpoint，也不强绑 JDBC。需要直接读取 ActionGraph trace 表时，再引入 `actiongraph-console-jdbc`；需要输出运行摘要 CSV 或 Trace CSV/JSONL 审计证据时，引入 `actiongraph-console-export` 即可。Spring MVC 控制层可以只引入 `actiongraph-console-api-spring-boot-starter` 暴露 JSON 查询端点，只引入 `actiongraph-console-ui-spring-boot-starter` 暴露内置页面，只引入 `actiongraph-console-export-spring-boot-starter` 暴露审计导出端点，或使用兼容聚合包 `actiongraph-console-spring-boot-starter` 同时引入 API 与 UI；如果希望仓储从 `DataSource` 自动创建，再叠加 `actiongraph-console-jdbc-spring-boot-starter`。启用 `actiongraph.console.enabled=true` 后，会暴露只读运行监控能力：

```text
UI starter:  GET /actiongraph/console
API starter: GET /actiongraph/console/runs?limit=50&offset=0&status=COMPLETED&auditComplete=true
API starter: GET /actiongraph/console/runs/{runId}
API starter: GET /actiongraph/console/runs/{runId}/trace
Export starter: GET /actiongraph/console/runs/export.csv
Export starter: GET /actiongraph/console/runs/{runId}/trace/export.csv
Export starter: GET /actiongraph/console/runs/{runId}/trace/export.jsonl
```

内置页面包含运行列表、筛选栏、选中运行元数据和 Trace 时间线。API 返回内容包含分页元数据、run 状态、首尾 Trace 时间、Trace 事件列表、Trace 事件数、审计链是否完整以及首个断链序号。导出端点基于同一只读服务输出可归档的 CSV/JSONL。配置 `actiongraph.console.shared-secret` 后，API 和导出访问方必须携带 Console token Header。只接 API 的服务可以服务企业统一控制台，只接 UI 的服务可以把页面壳接到自研 API 网关，只接导出的服务可以面向审计归档系统。

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
批量报告会输出 Markdown、CSV 和只读 HTML console，按样本拆出总耗时、业务 Action 耗时、框架调度耗时和审批等待耗时；`suspend-resume` / `external-decisions` / `external-callbacks` 审批模式会走真实挂起/恢复路径，并从审批任务时间戳计算等待。`external-callbacks` 会把 JSONL 审批回调投递给 `actiongraph-human-review` 中的 `HumanReviewCallbackHandler`，覆盖共享密钥校验和重复投递幂等。生产审批系统可以通过 `HumanReviewCallbackHandler` 写入审批结果，也可以引入 `actiongraph-human-review-callback-spring-boot-starter` 接收 HTTP 回调。

## 设计边界

ActionGraph 明确避免把企业系统交给 LLM 自由发挥：

- LLM 不生成 Plan。
- Planner 不调用业务代码，只做符号搜索。
- Runtime Guard 不进入 Planner，只在执行前判断真实业务值。
- 高风险 Action 默认走人工审批。
- 终态失败或拒绝触发逆序补偿。
- 所有关键事件写入 Trace。

## 当前成熟度

- 261 个自动化测试通过。
- 并发冒烟约 6000 runs/s；重复 resume 只产生一次业务副作用。
- 36 个 Gradle 模块完成拆分。
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
- [运行入口 Runtime API](docs/frameworkization/runtime-api.md)
- [治理策略 Spring Boot Starter](docs/frameworkization/governance-spring-boot-starter.md)
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
