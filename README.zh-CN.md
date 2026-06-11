# ActionGraph

[![CI](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml/badge.svg)](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml)

[官网](https://yyaa18.github.io/actiongraph/) · [License](LICENSE) · [安全政策](SECURITY.md) · [贡献指南](CONTRIBUTING.md) · [稳定契约](STABLE_CONTRACT.md) · [变更日志](CHANGELOG.md)

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
- **治理策略库**：脱敏、额度、规则权限/租户校验可作为非 Spring 组件独立引入；Spring 侧由主 starter 按配置装配。
- **SLF4J API 诊断日志**：planner、executor、补偿和挂起/恢复关键决策点提供 debug 日志，不绑定具体日志实现，生产服务可按自己的日志体系接管。
- **核心 API 合同 Javadoc**：Action、Policy、人审、挂起恢复、Blackboard、Trace 的线程安全、null、补偿、审计顺序和并发认领语义写入公开接口。
- **类型化异常层级**：输入错误、配置错误、并发冲突、资源不存在、外部集成失败都有稳定父类，Java 8 控制面客户端保持独立异常根。
- **Observability SPI**：core 暴露 provider-neutral `ObservationSink`，Spring starter 可自动接入 Micrometer，也允许业务系统替换成内部监控实现。
- **注解适配器**：普通 Java 方法可通过注解注册成 Action，非 Spring 服务也可独立使用。
- **Spring Boot Starter**：一个主 starter 收敛注解扫描、运行时默认 Bean、JDBC 仓储、结构化记忆、仓储型人审、治理策略，以及按配置启用的运行入口/组件目录/人审 HTTP 端点。
- **Human Review 组件**：审批任务仓储、审批链、回调处理和 repository-backed policy 独立成非 Spring 组件，可由审批服务、批处理或业务服务按需复用。
- **Human Review API**：审批任务查询与决策写入服务独立成纯 Java 组件，审批门户、CLI、企业网关可直接复用。
- **控制面端点组合**：运行入口、组件目录、审批任务与审批回调端点由主 starter 提供、各自配置门控；Console API/UI/导出端点保留为单独可选 starter。
- **Console 组件**：只读控制台查询、JDBC Trace read model、运行摘要 CSV 和 Trace CSV/JSONL 导出收敛成一个非 Spring 组件，可供自研控制台、CLI、批处理或网关适配复用。
- **Console Spring Boot Starter**：Console JSON API、内置页面、CSV/JSONL 审计导出端点与 JDBC Console 仓储自动装配收敛成一个可选控制层组件，能力仍由 `actiongraph.console.*` 配置门控。
- **结构化记忆组件**：Memory 记录、仓储接口、内存实现与 Blackboard 上下文加载器已进入 core，core-only 服务无需额外引入。
- **目标解释与 Runtime SPI**：GoalCatalog、GoalInterpreter、解释结果、Blackboard seeder、`ActionGraphRuntimeOperations` 启动/恢复接口在 core 中可直接复用；HTTP 端点只是主 Spring starter 的可选适配器。
- **批量目标解释 SPI**：批处理由业务系统拥有，框架提供 `BatchGoalInterpreter` 边界，便于接入方按模型、token 成本和数据来源实现分组、缓存或绕过 LLM 的解释策略。
- **Component Catalog**：框架模块、能力标签、依赖提示和推荐组合方案可作为 Java 8 兼容机器可读目录独立引入，方便企业网关、控制台和部署检查复用。
- **Control Plane API**：控制层响应契约、`.properties` 聚合配置、安全 GET 重试、统一聚合客户端、轻量 Runtime / Component Catalog / Human Review / Console HTTP client 与共享密钥 token 校验抽成 Java 8 兼容纯 Java 组件，老系统可不引入 Spring 直接调用已部署的 ActionGraph Runtime API、组件目录端点、审批任务端点和只读审计端点。
- **兼容性标签**：组件目录为每个公开模块暴露 `compatibility`，明确区分 Java 8 client 边界、现代运行时模块和样例模块，避免老旧项目误引不可加载的 jar。
- **LLM Goal Interpreter**：通用解释器、GoalCatalog prompt 渲染和结构化输出解析独立成组件；DeepSeek 兼容客户端作为可选 provider 引入，LLM 故障可降级到规则解释器。
- **JDBC 持久化**：核心模块支持 Trace、挂起运行和 Trace read model；审批任务与结构化记忆分别有独立 JDBC 组件，Trace 批量写入降低运行路径开销，挂起快照恢复支持 Blackboard 类型白名单和格式版本校验。
- **只读 Console Read Model**：JDBC Trace 表可分页/筛选查询运行摘要、Trace 详情、终态/挂起状态和审计链验证结果，支撑后续服务化监管视图。
- **1.0 工程治理文件**：Apache 2.0 License、安全政策、贡献指南、稳定契约与变更日志已经进入仓库根目录，作为正式发版前的治理底座。

## 模块结构

| 模块 | 作用 |
|---|---|
| `actiongraph-bom` | Maven / Gradle BOM，用于统一 ActionGraph 各组件版本 |
| `actiongraph-core` | 核心 Action、注解适配器、Planning、Runtime、Policy、Trace、Observability SPI、公开合同 Javadoc、SLF4J API 诊断日志、目标解释契约、运行入口与结构化记忆 API |
| `actiongraph-control-plane-api` | Java 8 兼容的组件目录、控制层响应契约、`.properties` 聚合配置、安全 GET 重试、统一聚合 / Runtime / Component Catalog / Human Review / Console HTTP 客户端与共享密钥 token 校验 |
| `actiongraph-human-review` | 可选审批任务仓储、回调处理器、审批链、repository-backed policy 与任务查询/决策服务 |
| `actiongraph-governance` | 可选非 Spring 治理策略：脱敏、额度、规则权限/租户校验、额度审批属性与风险审批链路由 |
| `actiongraph-llm-deepseek` | 可选 LLM 包：通用目标解释、GoalCatalog prompt 渲染、结构化输出解析与 DeepSeek 兼容客户端 |
| `actiongraph-persistence-jdbc` | Trace、挂起运行、Trace read model、结构化记忆与审批任务的 JDBC 仓储 |
| `actiongraph-spring-boot-starter` | 主 Spring Boot 集成：注解扫描、运行时默认 Bean、可选 Micrometer 观测、JDBC 仓储、结构化记忆、仓储型人审、治理策略，以及按配置启用的运行入口/组件目录/人审/Console HTTP 端点 |
| `actiongraph-console` | 可复用只读 Console 查询服务、JDBC read model 与 CSV/JSONL 审计导出服务 |
| `actiongraph-samples` | 续约报价、订单取消、理赔资料预审三个完整参考域 |

## 快速接入

Spring Boot 应用通常引入：

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))

    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
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
  component-catalog:
    enabled: true
    path: /actiongraph/components
    token-header: X-ActionGraph-Catalog-Token
    shared-secret: ${ACTIONGRAPH_CATALOG_SECRET}
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

引入 `actiongraph-spring-boot-starter` 并启用 `actiongraph.persistence.jdbc.enabled=true` 后，Spring Boot 应用只要提供 `DataSource`，Trace、挂起运行、Trace read model、结构化记忆和审批任务都会切到 JDBC 实现。非 Spring 服务或需要完全手工控制的服务，可以直接使用底层 `actiongraph-persistence-jdbc`，手工组装运行时、结构化记忆或审批任务仓储。

非 Spring 服务如果需要框架内置的脱敏、额度、规则权限/租户校验、额度审批属性或风险审批链路由，可以直接依赖 `actiongraph-governance`，手工组装对应策略，不需要引入 Spring starter。

非 Spring 服务如果只需要结构化长期记忆，可以直接依赖 `actiongraph-core`，手工使用 `MemoryRepository` 与 `MemoryContextLoader`，不需要引入 Spring、JDBC 或 LLM 组件。

Spring 服务通过 `actiongraph-spring-boot-starter` 获取结构化长期记忆默认实现和 `MemoryContextLoader`；启用 `actiongraph.persistence.jdbc.enabled=true` 且存在 `DataSource` 时会自动让位给 JDBC `MemoryRepository`。

非 Spring 服务如果只需要目标目录、规则解释器、解释结果到 Blackboard 的装配桥，或把“解释目标 → 装配 Blackboard → 启动运行 / 恢复运行”作为稳定入口服务，可以直接依赖 `actiongraph-core` 的 `ActionGraphRuntimeOperations`。默认实现只组合 `GoalInterpreter`、`GoalBlackboardSeederRegistry`、`GoapExecutor` 和 `ActionRegistry`，启动/恢复的 metadata 重载可把请求流水号、来源系统等写入 Trace，不自带 LLM provider、不创建持久化，也不强制 HTTP 形态。业务系统可以围绕这个接口实现自己的 Controller、MQ 消费者、调度任务、ESB 适配器或批处理 Worker。Spring MVC 服务需要入口端点时，再引入 `actiongraph-spring-boot-starter` 并启用 `actiongraph.runtime.api.enabled=true`：

```text
Runtime API: POST /actiongraph/runtime/interpret
Runtime API: POST /actiongraph/runtime/runs
Runtime API: POST /actiongraph/runtime/runs/{runId}/resume
```

Runtime API Spring MVC starter 只会把 `actiongraph.runtime.api.trace-headers` 配置的请求头写入 `RUN_STARTED` / `RUN_RESUMED` Trace 元数据；默认包含 `X-Request-Id`、`X-Correlation-Id` 和 `X-Source-System`。已配置的运行入口 token Header 即使被误加入 `trace-headers`，也不会被复制进 Trace 元数据。

生产系统不应该通过 Gradle 或样例命令行来触发 ActionGraph 运行。批处理语义属于业务系统：框架只提供 `BatchGoalInterpreter` 作为批量解释 SPI，接入方可以按模型能力、上下文窗口、缓存命中率和源数据结构实现 token 高效策略；如果来源记录已经结构化，应直接生成 goalType 与 known parameters，绕过 LLM。Java 8 调用方可以依赖 `ActionGraphRuntimeGateway` 接口，并使用 `ActionGraphRuntimeHttpClient` 作为访问已部署 ActionGraph 服务的默认实现。

非 Spring 服务、CLI、企业网关、部署检查或 Java 8 老系统如果需要结构化读取 ActionGraph 的模块、能力标签、依赖提示、兼容性标签和推荐组合方案，可以直接依赖 `actiongraph-control-plane-api`。Spring MVC 控制层需要只读目录端点时，再引入 `actiongraph-spring-boot-starter` 并启用 `actiongraph.component-catalog.enabled=true`：

```text
Catalog API: GET /actiongraph/components
Catalog API: GET /actiongraph/components/modules
Catalog API: GET /actiongraph/components/modules/{module}
Catalog API: GET /actiongraph/components/modules/{module}/profiles
Catalog API: GET /actiongraph/components/compatibility/{compatibility}
Catalog API: GET /actiongraph/components/profiles
Catalog API: GET /actiongraph/components/profiles/{profile}
```

自研网关、endpoint adapter、Java 8 老系统、Java 8 审批门户或 Java 8 审计控制台如果需要复用控制层错误响应结构、通过 HTTP 调用已部署的 ActionGraph Runtime API、组件目录端点、人审任务查询/决策/回调端点、Console 只读运行/Trace 查询和 CSV/JSONL 审计导出端点，或复用共享密钥 token 校验，可以直接依赖 `actiongraph-control-plane-api`。它提供统一的 `ControlPlaneErrorResponse(error, message)`、可从一个 `/actiongraph` 根路径派生全部控制层端点的 `ActionGraphControlPlaneHttpClient`、可从 `.properties` / 配置中心键构建聚合客户端的 `ActionGraphControlPlaneHttpClientProperties`、针对查询类 GET 的可选安全重试、零外部依赖的 `ActionGraphRuntimeHttpClient` / `ActionGraphComponentCatalogHttpClient` / `ActionGraphHumanReviewHttpClient` / `ActionGraphConsoleHttpClient`，以及 `ControlPlaneTokenVerifier` / `SharedSecretTokenProperties`；内置 Spring 端点与 Console 端点会传递依赖它。GET 重试默认关闭，仅覆盖组件目录、人审任务查询和 Console 只读查询；可能产生副作用的 POST 不会自动重试。token helper 负责校验 Header 名、在未配置 `shared-secret` 时跳过 token 读取、使用常量时间比较 token；它不是企业 IAM / RBAC 层，生产身份、权限和网关策略仍由接入系统或后续治理组件负责。服务端白名单接收的请求 Header 会作为 Trace metadata 写入审计链；高风险运行挂起人审时，同一批 metadata 也会进入审批任务 attributes，保证老系统交易流水号、来源系统和关联 ID 在审批链路中仍可见。Runtime API 端点会额外硬排除已配置的 token Header，避免共享密钥进入审计链。

组件目录会为每个模块暴露 `compatibility` 标签。当前 `actiongraph-control-plane-api` 是 Java 8 应用可以直接引入的 `java8-client` artifact；可嵌入 runtime、Spring、JDBC、治理、LLM、Console 与样例侧仍属于 `java21-plus` 或 `sample-only`，应部署在现代 ActionGraph 服务侧，老旧系统通过 HTTP、企业网关、ESB 或 Java 8+ sidecar 接入。

非 Spring 服务如果需要审批任务仓储、审批链、审批回调处理或稳定的任务查询与决策服务，可以直接依赖 `actiongraph-human-review`，不需要引入 Spring MVC endpoint。

引入 `actiongraph-spring-boot-starter` 后，治理自动装配可用；`actiongraph.masking.enabled=true` 启用脱敏，配置 `actiongraph.limits.rules` 启用额度规则，`actiongraph.human-review.risk-based-approval-chain=true` 启用风险审批链路由。没有配置这些开关时保持中性默认值。

引入 `actiongraph-spring-boot-starter` 后，Spring 服务会获得仓储型人工审批默认装配。该 starter 默认提供内存 `HumanReviewRepository`，启用 `actiongraph.persistence.jdbc.enabled=true` 且存在 `DataSource` 时会使用持久化实现。任务列表、运行维度查询、详情、决策端点和外部审批系统 HTTP 回调仍分别由 `actiongraph.human-review.api.enabled=true` 与 `actiongraph.human-review.callback-endpoint.enabled=true` 控制。

```text
Human Review API: GET  /actiongraph/human-review/tasks/pending
Human Review API: GET  /actiongraph/human-review/tasks/runs/{runId}
Human Review API: GET  /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}
Human Review API: POST /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}/decision
Human Review Callback: POST /actiongraph/human-review/callbacks
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

自研控制台、CLI 或企业网关适配可以直接依赖 `actiongraph-console`，复用只读查询服务、JSON 响应模型、`ConsoleRunRepository` 端口、JDBC Trace read model 与 CSV/JSONL 审计导出服务，不引入 Spring MVC endpoint。Spring MVC 控制层引入 `actiongraph-spring-boot-starter`，即可获得内置 JSON API、页面、导出端点，以及可选的 `DataSource` 到 `ConsoleRunRepository` 自动装配。启用 `actiongraph.console.enabled=true` 后，会暴露只读运行监控能力：

如果单个 Spring MVC 部署需要同时暴露运行入口、组件目录、审批任务、审批回调和 Console API/UI/导出端点，引入 `actiongraph-spring-boot-starter`，再按部署边界打开对应 `enabled` 配置即可。starter 不会替你创建业务 Action、LLM client 或领域解释器。

```text
Console starter: GET /actiongraph/console
Console starter: GET /actiongraph/console/runs?limit=50&offset=0&status=COMPLETED&auditComplete=true
Console starter: GET /actiongraph/console/runs/{runId}
Console starter: GET /actiongraph/console/runs/{runId}/trace
Console starter: GET /actiongraph/console/runs/export.csv
Console starter: GET /actiongraph/console/runs/{runId}/trace/export.csv
Console starter: GET /actiongraph/console/runs/{runId}/trace/export.jsonl
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
批量报告会输出 Markdown、CSV 和只读 HTML console，按样本拆出总耗时、业务 Action 耗时、框架调度耗时和审批等待耗时；`suspend-resume` / `external-decisions` / `external-callbacks` 审批模式会走真实挂起/恢复路径，并从审批任务时间戳计算等待。`external-callbacks` 会把 JSONL 审批回调投递给 `actiongraph-human-review` 中的 `HumanReviewCallbackHandler`，覆盖共享密钥校验和重复投递幂等。生产审批系统可以通过 `HumanReviewCallbackHandler` 写入审批结果，也可以引入 `actiongraph-spring-boot-starter` 接收 HTTP 回调。

## 设计边界

ActionGraph 明确避免把企业系统交给 LLM 自由发挥：

- LLM 不生成 Plan。
- Planner 不调用业务代码，只做符号搜索。
- Runtime Guard 不进入 Planner，只在执行前判断真实业务值。
- 高风险 Action 默认走人工审批。
- 终态失败或拒绝触发逆序补偿。
- 所有关键事件写入 Trace。

## 当前成熟度

- 351 个自动化测试通过。
- 并发冒烟约 6000 runs/s；重复 resume 只产生一次业务副作用。
- Gradle 模块已收敛到 10 个：core、control-plane-api、governance、human-review、persistence-jdbc、llm-deepseek、console、spring-boot-starter、bom 与 samples。
- 3 个参考业务域完整跑通。
- 支持模块发布到 Maven Local / 私服。
- 支持 Spring Boot 注解式接入。
- 支持 suspend / resume、JDBC persistence、human review、structured memory。
- F0 内核金融化完成：Trace/审批预览敏感数据脱敏、审计链防篡改、多级审批链、单笔额度策略。
- F1 试点准备已具备工程样板：理赔资料预审 + 赔付申请草稿样板域已跑通，并支持 CSV / JDBC 样本、PostgreSQL 脱敏视图映射、外部审批决策、JSONL 回调和 Markdown/CSV/HTML 指标报告。F1 是否成立仍以真实/准真实环境落地、业务方续用意愿和案例证据为门槛；样板域已冻结新增演示能力，只允许补缺陷、真实/准真实适配和验收证据。

## 文档

- [快速接入指南](docs/quick-start.html)
- [真实 LLM 冒烟测试](docs/frameworkization/llm-smoke.md)
- [人工审批集成](docs/frameworkization/human-review.md)
- [运行入口 Runtime API](docs/frameworkization/runtime-api.md)
- [运行入口 SPI 与批量解释边界](docs/frameworkization/runtime-invocation-spi.md)
- [Observability SPI 与 Micrometer](docs/frameworkization/observability-spi.md)
- [组件目录 Component Catalog](docs/frameworkization/component-catalog.md)
- [控制层响应契约](docs/frameworkization/control-plane-api.md)
- [Java 8 老系统接入](docs/frameworkization/java8-legacy-integration.md)
- [Java 8 Maven 消费者示例](docs/examples/java8-maven-consumer)
- [Java 8 组件目录客户端示例](docs/examples/java8-component-catalog-client)
- [Java 8 聚合控制面客户端示例](docs/examples/java8-control-plane-client)
- [Java 8 组件目录 HTTP 客户端示例](docs/examples/java8-catalog-http-client)
- [Java 8 人审 HTTP 客户端示例](docs/examples/java8-human-review-client)
- [Java 8 Console HTTP 客户端示例](docs/examples/java8-console-client)
- [Java 8 老系统客户端示例](docs/examples/java8-legacy-client)
- [Raw HTTP 网关协议参考](docs/examples/raw-http-gateway-contract)
- [控制面端点组合](docs/frameworkization/control-plane-starter.md)
- [治理策略 Spring Boot Starter](docs/frameworkization/governance-spring-boot-starter.md)
- [理赔预审 PostgreSQL 映射](docs/frameworkization/claims-precheck-postgresql.md)
- [理赔预审审批回调重放](docs/frameworkization/claims-precheck-review-callbacks.md)
- [理赔预审只读 Console](docs/frameworkization/claims-precheck-console.md)
- [F1 准备状态](docs/f1-readiness-status.md)
- [F1 试点验收包](docs/f1-pilot-validation-pack.md)
- [F1 试点证据模板](docs/examples/f1-pilot-evidence-template)
- [依赖组合指南](docs/frameworkization/dependency-composition.md)
- [生态组件模块化](docs/frameworkization/ecosystem-modularity.md)
- [技术汇报演示文档](docs/actiongraph-pitch.html)
- [框架化笔记](docs/frameworkization/)
- [原始 PRD](docs/PRD-v0.md)
- [F0 金融化 PRD](docs/PRD-F0-finance.md)
- [F1 理赔预审样板域笔记](docs/f1-claims-precheck-notes.md)

## 项目治理

- [License](LICENSE)
- [安全政策](SECURITY.md)
- [贡献指南](CONTRIBUTING.md)
- [稳定契约与兼容性政策](STABLE_CONTRACT.md)
- [变更日志](CHANGELOG.md)

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
