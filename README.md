# ActionGraph

[![CI](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml/badge.svg)](https://github.com/YYaa18/actiongraph/actions/workflows/ci.yml)

[中文文档](README.zh-CN.md)

ActionGraph is a typed GOAP framework for governed business action execution in Java.

It lets application teams expose ordinary business methods as typed Actions, then uses a deterministic symbolic planner to compose those Actions into auditable execution paths. LLMs can interpret user goals, but they do not generate or execute plans.

## What It Provides

- Deterministic GOAP planning over Action preconditions and effects
- Runtime guards for value-dependent business checks
- Multi-stage human review with atomic suspend/resume claiming
- Saga-style compensation for failed or denied runs
- Trace, suspended-run, review-task, and memory repositories, including batched JDBC trace writes
- JDBC read model for paged and filtered run summaries, trace details, and trace-chain verification in read-only consoles
- Suspended-run Blackboard type allowlists for safer JDBC resume
- Suspended-run snapshot format version checks for deployment discipline
- Optional data masking for trace details/data and human-review previews
- Tamper-evident TraceEvent hash chains with verification support
- Single-transaction amount limits with hard denial and review escalation
- Spring Boot starter with annotation-driven Action registration and runtime defaults
- Optional governance Spring Boot starter for masking, amount limits, and approval routing
- Optional human-review Spring Boot starter with approval callback endpoint support
- Reusable console core service for read-only run monitoring
- Optional JDBC adapter for the console query port
- Optional console Spring Boot starter with read-only run monitoring UI/endpoints
- Optional JDBC Spring Boot starter for durable repository auto-configuration
- DeepSeek-compatible LLM goal interpretation
- Reference samples for renewal quote, order cancellation, and claims precheck flows

## Modules

| Module | Purpose |
|---|---|
| `actiongraph-bom` | Maven/Gradle BOM for aligning ActionGraph module versions |
| `actiongraph-core` | Core action, planning, runtime, policy, trace, memory, and interpretation APIs |
| `actiongraph-llm-deepseek` | DeepSeek-compatible LLM client and GoalCatalog prompt support |
| `actiongraph-persistence-jdbc` | JDBC repositories for trace, suspended runs, human review, and memory |
| `actiongraph-spring-boot-starter` | Spring Boot auto-configuration and annotation scanning |
| `actiongraph-governance-spring-boot-starter` | Optional Spring Boot governance policies for masking, amount limits, and approval routing |
| `actiongraph-jdbc-spring-boot-starter` | Optional Spring Boot auto-configuration for JDBC repositories |
| `actiongraph-human-review-spring-boot-starter` | Optional approval callback endpoint for external review systems |
| `actiongraph-console-core` | Reusable read-only console query service and response model |
| `actiongraph-console-jdbc` | Optional JDBC adapter for the console query port |
| `actiongraph-console-spring-boot-starter` | Optional read-only Console UI and Spring MVC query endpoints |
| `actiongraph-samples` | Pure Java sample applications |

## Quick Start

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))

    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    // Optional ecosystem/control-plane components:
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
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

When `actiongraph-jdbc-spring-boot-starter` is on the classpath and `actiongraph.persistence.jdbc.enabled=true`, Spring Boot applications with a `DataSource` automatically get JDBC-backed trace, suspended-run, review-task, memory, and console read-model repositories. Non-Spring services can still use `actiongraph-persistence-jdbc` directly and wire repositories by hand.

When `actiongraph-governance-spring-boot-starter` is on the classpath, masking, amount-limit rules, and risk-based approval-chain properties are activated. Without it, the base Spring starter keeps neutral defaults: no masking, default permission allow, no amount escalation, and single-stage review.

When `actiongraph-human-review-spring-boot-starter` is on the classpath and the callback endpoint is enabled in a Spring MVC application, approval systems can post decisions directly. The endpoint requires a `HumanReviewRepository` bean; `actiongraph-spring-boot-starter` supplies an in-memory default, and the JDBC starter supplies a durable production bean when enabled.

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

`actiongraph-console-core` can be used directly by custom monitoring services that want the run query service, response model, and `ConsoleRunRepository` port without Spring MVC or JDBC coupling. Add `actiongraph-console-jdbc` when that custom service wants to read ActionGraph trace tables through JDBC. When `actiongraph-console-spring-boot-starter` is on the classpath and `actiongraph.console.enabled=true`, a Spring MVC application with a `DataSource` exposes read-only run monitoring endpoints:

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
Batch reports include Markdown, CSV, and a read-only HTML console with total runtime, business action time, framework overhead, and review wait time for each case. The `suspend-resume`, `external-decisions`, and `external-callbacks` review modes use the real suspended-run resume path and derive approval latency from review task timestamps. Production approval integrations can write decisions through `HumanReviewCallbackHandler` or enable the optional Spring Boot callback endpoint above.
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
