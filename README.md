# ActionGraph

[![CI](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml/badge.svg)](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml)

[中文文档](README.zh-CN.md)

ActionGraph is a typed GOAP framework for governed business action execution in Java.

It lets application teams expose ordinary business methods as typed Actions, then uses a deterministic symbolic planner to compose those Actions into auditable execution paths. LLMs can interpret user goals, but they do not generate or execute plans.

## What It Provides

- Deterministic GOAP planning over Action preconditions and effects
- Runtime guards for value-dependent business checks
- Optional repository-backed multi-stage human review with atomic suspend/resume claiming
- Saga-style compensation for failed or denied runs
- Trace, suspended-run, review-task, and optional memory repositories, including batched JDBC trace writes
- JDBC read model for paged and filtered run summaries, trace details, and trace-chain verification in read-only consoles
- Suspended-run Blackboard type allowlists for safer JDBC resume
- Suspended-run snapshot format version checks for deployment discipline
- Optional data masking for trace details/data and human-review previews
- Tamper-evident TraceEvent hash chains with verification support
- Single-transaction amount limits with hard denial and review escalation
- Reusable non-Spring governance policies for masking, amount limits, and rule-based permissions
- Optional human-review governance extension for review attributes and risk-based approval routing
- Optional pure Java annotation adapter for registering ordinary methods as Actions
- Optional structured memory context component
- Optional Spring Boot starter for structured memory
- Optional non-Spring human review tasks, callback handling, and approval chains
- Spring Boot starter with annotation-driven Action registration and runtime defaults
- Optional governance Spring Boot starter for masking, amount limits, and rule-based permissions
- Optional human-review governance Spring Boot starter for review attributes and approval-chain routing
- Optional human-review Spring Boot starter with repository-backed review policy support
- Optional human-review callback Spring Boot starter with approval callback endpoint support
- Reusable console core service for read-only run monitoring
- Optional JDBC adapter for the console query port
- Optional JDBC Spring Boot starter for console repository auto-configuration
- Optional console Spring Boot starter with read-only run monitoring UI/endpoints
- Optional JDBC Spring Boot starter for durable repository auto-configuration
- Provider-neutral LLM goal interpretation, prompt rendering, and structured output parsing
- Optional goal interpretation contracts and GoalCatalog metadata
- DeepSeek-compatible LLM client
- Reference samples for renewal quote, order cancellation, and claims precheck flows

## Modules

| Module | Purpose |
|---|---|
| `actiongraph-bom` | Maven/Gradle BOM for aligning ActionGraph module versions |
| `actiongraph-core` | Core action, planning, runtime, policy, and trace APIs |
| `actiongraph-annotations` | Optional pure Java annotations and adapter for registering ordinary methods as Actions |
| `actiongraph-memory` | Optional structured memory records, repository contract, in-memory implementation, and Blackboard context loader |
| `actiongraph-memory-spring-boot-starter` | Optional Spring Boot auto-configuration for structured memory |
| `actiongraph-interpretation` | Optional goal interpretation contracts, GoalCatalog metadata, and Blackboard seeders |
| `actiongraph-human-review` | Optional repository-backed human review tasks, callback handler, and approval-chain support |
| `actiongraph-governance` | Optional non-Spring governance policies for masking, amount limits, and rule-based permissions |
| `actiongraph-governance-human-review` | Optional non-Spring human-review governance extension for amount review attributes and risk-based approval routing |
| `actiongraph-llm` | Provider-neutral LLM goal interpretation, GoalCatalog prompt rendering, and structured output parsing |
| `actiongraph-llm-deepseek` | Optional DeepSeek-compatible LLM client; brings `actiongraph-llm` transitively |
| `actiongraph-persistence-jdbc` | Core JDBC repositories for trace, suspended runs, and trace read model |
| `actiongraph-memory-jdbc` | Optional JDBC repository for structured memory |
| `actiongraph-human-review-jdbc` | Optional JDBC repository for human-review tasks |
| `actiongraph-spring-boot-starter` | Spring Boot auto-configuration and annotation scanning; brings `actiongraph-annotations` transitively |
| `actiongraph-governance-spring-boot-starter` | Optional Spring Boot governance policies for masking, amount limits, and rule-based permissions |
| `actiongraph-governance-human-review-spring-boot-starter` | Optional Spring Boot human-review governance policies for amount review attributes and approval-chain routing |
| `actiongraph-jdbc-spring-boot-starter` | Optional Spring Boot auto-configuration for core JDBC repositories |
| `actiongraph-memory-jdbc-spring-boot-starter` | Optional Spring Boot auto-configuration for JDBC memory repository |
| `actiongraph-human-review-jdbc-spring-boot-starter` | Optional Spring Boot auto-configuration for JDBC human-review repository |
| `actiongraph-human-review-spring-boot-starter` | Optional repository-backed review policy auto-configuration |
| `actiongraph-human-review-callback-spring-boot-starter` | Optional Spring MVC approval callback endpoint for external review systems |
| `actiongraph-console-core` | Reusable read-only console query service and response model |
| `actiongraph-console-jdbc` | Optional JDBC adapter for the console query port |
| `actiongraph-console-jdbc-spring-boot-starter` | Optional Spring Boot auto-configuration for the JDBC console repository |
| `actiongraph-console-spring-boot-starter` | Optional read-only Console UI and Spring MVC query endpoints |
| `actiongraph-samples` | Pure Java sample applications |

## Quick Start

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))

    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-memory-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    // Optional ecosystem/control-plane components:
    implementation("com.actiongraph:actiongraph-memory-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-callback-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

```java
@ActionGraphAction(
        id = "order.lookup",
        preconditions = "order-cancellation:ORDER_ID_PRESENT",
        effects = "order-cancellation:ORDER_LOADED"
)
public OrderRecord lookup(OrderId orderId) {
    return orderService.find(orderId);
}
```

Spring configuration uses the `actiongraph.*` prefix:

```yaml
actiongraph:
  planner:
    max-depth: 32
  executor:
    max-steps: 64
  masking:
    enabled: false
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

When `actiongraph-jdbc-spring-boot-starter` is on the classpath and `actiongraph.persistence.jdbc.enabled=true`, Spring Boot applications with a `DataSource` automatically get JDBC-backed trace, suspended-run, and trace read-model repositories. Add `actiongraph-memory-jdbc-spring-boot-starter` and/or `actiongraph-human-review-jdbc-spring-boot-starter` when structured memory or review tasks also need JDBC durability. Non-Spring services can still use `actiongraph-persistence-jdbc`, `actiongraph-memory-jdbc`, and `actiongraph-human-review-jdbc` directly and wire repositories by hand.

Non-Spring services can use `actiongraph-governance` directly when they want packaged masking, amount-limit, or rule-based permission policies without Spring auto-configuration. Add `actiongraph-governance-human-review` only when review attributes or risk-based approval-chain routing are needed.

Non-Spring services can use `actiongraph-memory` directly when they want structured long-term memory without adopting Spring, JDBC, or LLM modules.

Spring services can add `actiongraph-memory-spring-boot-starter` when they want in-memory structured memory defaults and `MemoryContextLoader`. If `actiongraph-memory-jdbc-spring-boot-starter` is also enabled, the memory starter backs off to the JDBC `MemoryRepository`.

Non-Spring services can use `actiongraph-interpretation` directly when they want GoalCatalog metadata, rule-based goal interpreters, or Goal-to-Blackboard seeding without adopting an LLM provider.

Non-Spring services can use `actiongraph-human-review` directly when they need external approval task storage, callback handling, or multi-stage approval chains without Spring MVC.

When `actiongraph-governance-spring-boot-starter` is on the classpath, masking and amount-limit permission rules are activated. Add `actiongraph-governance-human-review-spring-boot-starter` when those limit rules should also enrich human-review requests or when `actiongraph.human-review.risk-based-approval-chain=true` should route approval stages. Without these modules, the base Spring starter keeps neutral defaults: no masking, default permission allow, no amount escalation, and safe pending human review.

When `actiongraph-human-review-spring-boot-starter` is on the classpath, Spring services get repository-backed human review defaults. The starter supplies an in-memory `HumanReviewRepository` by default, and `actiongraph-human-review-jdbc-spring-boot-starter` supplies a durable production bean when enabled. Add `actiongraph-human-review-callback-spring-boot-starter` and enable `actiongraph.human-review.callback-endpoint.enabled=true` in a Spring MVC application to let approval systems post decisions directly.

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

If `shared-secret` is configured, the request must include the configured token header with the same value. Missing or invalid callback tokens return `401 UNAUTHORIZED`.

`actiongraph-console-core` can be used directly by custom monitoring services that want the run query service, response model, and `ConsoleRunRepository` port without Spring MVC or JDBC coupling. Add `actiongraph-console-jdbc` when that custom service wants to read ActionGraph trace tables through JDBC. Spring MVC control-plane services add `actiongraph-console-spring-boot-starter` for the page and HTTP endpoints, then provide any `ConsoleRunRepository` bean. Add `actiongraph-console-jdbc-spring-boot-starter` when that repository should be auto-created from a `DataSource`. With `actiongraph.console.enabled=true`, the read-only endpoints are:

```text
GET /actiongraph/console
GET /actiongraph/console/runs?limit=50&offset=0&status=COMPLETED&auditComplete=true
GET /actiongraph/console/runs/{runId}
GET /actiongraph/console/runs/{runId}/trace
```

The built-in page provides a run list, filters, selected-run metadata, and a trace timeline. API responses include paging metadata, run status, first/last trace timestamps, trace event count, trace details, and trace-chain verification results. Configure `actiongraph.console.shared-secret` to require the console token header for API calls.

## Build And Test

```bash
./gradlew build
```

Run the sample apps:

```bash
./gradlew :actiongraph-samples:run --args="--approve-human-review Prepare renewal quote for C123"
./gradlew :actiongraph-samples:runOrderCancellationSample --args="--approve-human-review Cancel order O100"
./gradlew :actiongraph-samples:runClaimsPrecheckSample --args="--approve-human-review Prepare payout application for claim CLM100"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-001 --environment local"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-REVIEW-001 --environment local --review-mode suspend-resume --simulate-review-wait-ms 5"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --review-decisions actiongraph-samples/src/main/resources/claims-precheck-review-decisions.csv --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-EXTERNAL-REVIEWS --environment local"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args="--input actiongraph-samples/src/main/resources/claims-precheck-cases.csv --review-callbacks actiongraph-samples/src/main/resources/claims-precheck-review-callbacks.jsonl --review-callback-secret review-secret --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-CALLBACKS --environment local"
./gradlew :actiongraph-samples:runClaimsPrecheckBatchMetrics --args='--jdbc-url jdbc:postgresql://db.example/claims --jdbc-user actiongraph_reader --report-dir actiongraph-samples/build/reports/claims-precheck --batch-id F1-CLAIMS-JDBC-001 --environment staging'
```

The JDBC batch input path uses standard `DriverManager`; add the target database driver to the sample runtime classpath before running against a real database. See `actiongraph-samples/src/main/resources/sql/claims-precheck-source-contract.sql` for the anonymized view contract.
For PostgreSQL staging connections, see the dialect mapping in `actiongraph-samples/src/main/resources/sql/postgresql/claims-precheck-source-contract.sql` and [Claims Precheck PostgreSQL Mapping](docs/frameworkization/claims-precheck-postgresql.md).
Batch reports include Markdown, CSV, and a read-only HTML console with total runtime, business action time, framework overhead, and review wait time for each case. The `suspend-resume`, `external-decisions`, and `external-callbacks` review modes use the real suspended-run resume path and derive approval latency from review task timestamps. Production approval integrations can write decisions through `HumanReviewCallbackHandler` from `actiongraph-human-review` or enable the optional Spring Boot callback endpoint from `actiongraph-human-review-callback-spring-boot-starter`.
The `external-callbacks` mode replays JSONL approval callback deliveries through `HumanReviewCallbackHandler`, including shared-secret checks and duplicate-delivery idempotency.

## Documentation

- [Quick start guide](docs/quick-start.html)
- [Real LLM smoke test](docs/frameworkization/llm-smoke.md)
- [Human review integration](docs/frameworkization/human-review.md)
- [Governance Spring Boot starter](docs/frameworkization/governance-spring-boot-starter.md)
- [Claims precheck PostgreSQL mapping](docs/frameworkization/claims-precheck-postgresql.md)
- [Claims precheck review callbacks](docs/frameworkization/claims-precheck-review-callbacks.md)
- [Claims precheck read-only console](docs/frameworkization/claims-precheck-console.md)
- [Dependency composition](docs/frameworkization/dependency-composition.md)
- [Ecosystem modularity](docs/frameworkization/ecosystem-modularity.md)
- [Framework notes](docs/frameworkization/)
- [Original PRD](docs/PRD-v0.md)
- [F0 financialization PRD](docs/PRD-F0-finance.md)
- [F1 claims precheck notes](docs/f1-claims-precheck-notes.md)
