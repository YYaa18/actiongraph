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
- Reusable runtime API service for goal interpretation, start, resume, and request metadata trace capture
- Java 8 compatible machine-readable component catalog and composition profiles
- Java 8 compatible control-plane contracts plus properties-based aggregate configuration, safe GET retries, and lightweight aggregate, runtime, component catalog, human-review, and console HTTP clients
- Machine-readable compatibility labels for distinguishing Java 8 client artifacts from modern runtime modules
- Reusable control-plane shared-secret token verification component
- Optional non-Spring human review tasks, callback handling, and approval chains
- Reusable human-review task API service for approval inboxes and gateways
- Primary Spring Boot starter for annotation scanning, runtime defaults, JDBC repositories, memory, governance, repository-backed human review, and opt-in runtime/catalog/review endpoints
- Property-gated control-plane surfaces for runtime entry, component catalog, human-review task APIs, and approval callbacks
- Reusable console library for read-only run monitoring, JDBC read models, and CSV/JSONL audit evidence
- Optional console Spring Boot starter for JSON API, built-in UI, audit exports, and JDBC read-model repository auto-configuration
- Provider-neutral LLM goal interpretation, prompt rendering, and structured output parsing
- Optional goal interpretation contracts and GoalCatalog metadata
- DeepSeek-compatible LLM client
- Reference samples for renewal quote, order cancellation, and claims precheck flows

## Modules

| Module | Purpose |
|---|---|
| `actiongraph-bom` | Maven/Gradle BOM for aligning ActionGraph module versions |
| `actiongraph-core` | Core action, planning, runtime, policy, trace, goal interpretation, runtime entry, and structured memory APIs |
| `actiongraph-annotations` | Optional pure Java annotations and adapter for registering ordinary methods as Actions |
| `actiongraph-control-plane-api` | Java 8 compatible component catalog, control-plane response contracts, properties-based aggregate configuration, safe GET retries, lightweight aggregate / Runtime / Component Catalog / Human Review / Console HTTP clients, and shared-secret token verification |
| `actiongraph-human-review` | Optional repository-backed human review tasks, callback handler, approval-chain support, and task query/decision service |
| `actiongraph-governance` | Optional non-Spring governance policies for masking, amount limits, rule-based permissions, amount review attributes, and risk-based approval routing |
| `actiongraph-llm-deepseek` | Optional LLM package with provider-neutral goal interpretation, GoalCatalog prompt rendering, structured output parsing, and a DeepSeek-compatible client |
| `actiongraph-persistence-jdbc` | JDBC repositories for trace, suspended runs, trace read model, structured memory, and human-review tasks |
| `actiongraph-spring-boot-starter` | Main Spring Boot integration: annotation scanning, runtime defaults, JDBC repositories, structured memory, repository-backed human review, governance, and opt-in runtime/catalog/review HTTP endpoints |
| `actiongraph-console` | Reusable read-only Console query service, JDBC read model, and CSV/JSONL audit export service |
| `actiongraph-console-spring-boot-starter` | Optional Spring MVC Console API, UI, export endpoints, and JDBC repository auto-configuration |
| `actiongraph-samples` | Pure Java sample applications |

## Quick Start

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))

    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    // Optional read-only Console UI/API:
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

When `actiongraph-spring-boot-starter` is on the classpath and `actiongraph.persistence.jdbc.enabled=true`, Spring Boot applications with a `DataSource` automatically get JDBC-backed trace, suspended-run, trace read-model, memory, and human-review repositories. Non-Spring services can still use `actiongraph-persistence-jdbc` directly and wire runtime, memory, or human-review repositories by hand.

Non-Spring services can use `actiongraph-governance` directly when they want packaged masking, amount-limit, rule-based permission policies, review attributes, or risk-based approval-chain routing without Spring auto-configuration.

Non-Spring services can use `actiongraph-core` directly when they want structured long-term memory without adopting Spring, JDBC, or LLM modules.

Spring services get structured memory defaults and `MemoryContextLoader` from `actiongraph-spring-boot-starter`. It provides an in-memory `MemoryRepository` by default and backs off to a JDBC `MemoryRepository` when `actiongraph.persistence.jdbc.enabled=true` and a `DataSource` is available.

Non-Spring services can use `actiongraph-core` directly when they want GoalCatalog metadata, rule-based goal interpreters, Goal-to-Blackboard seeding, or the stable `interpret` / `start` / `resume` entry service without adopting an LLM provider or Spring MVC. The entry service composes a `GoalInterpreter`, `GoalBlackboardSeederRegistry`, `GoapExecutor`, and `ActionRegistry`, and its start/resume metadata overloads can record request ids or source systems in trace events. It does not provide an LLM provider, persistence, or HTTP endpoints by itself. Spring MVC applications can add `actiongraph-spring-boot-starter` and enable `actiongraph.runtime.api.enabled=true` to expose the same entry surface:

```text
Runtime API: POST /actiongraph/runtime/interpret
Runtime API: POST /actiongraph/runtime/runs
Runtime API: POST /actiongraph/runtime/runs/{runId}/resume
```

The Runtime API Spring MVC starter captures only configured `actiongraph.runtime.api.trace-headers` into `RUN_STARTED` / `RUN_RESUMED` trace metadata. Defaults are `X-Request-Id`, `X-Correlation-Id`, and `X-Source-System`. The configured runtime token header is never copied into trace metadata, even if accidentally listed in `trace-headers`.

Non-Spring services, CLIs, gateways, deployment checks, and Java 8 estates can use `actiongraph-control-plane-api` when they need a structured list of ActionGraph modules, capability tags, dependency hints, compatibility labels, and recommended composition profiles. Spring MVC control-plane services can add `actiongraph-spring-boot-starter` and enable `actiongraph.component-catalog.enabled=true` to expose the same read-only catalog:

```text
Catalog API: GET /actiongraph/components
Catalog API: GET /actiongraph/components/modules
Catalog API: GET /actiongraph/components/modules/{module}
Catalog API: GET /actiongraph/components/modules/{module}/profiles
Catalog API: GET /actiongraph/components/compatibility/{compatibility}
Catalog API: GET /actiongraph/components/profiles
Catalog API: GET /actiongraph/components/profiles/{profile}
```

Custom gateways, endpoint adapters, Java 8 legacy systems, Java 8 approval portals, or Java 8 audit consoles can use `actiongraph-control-plane-api` when they need ActionGraph's standard `{ "error", "message" }` error response contract, zero-dependency `ActionGraphControlPlaneHttpClient` for configuring all deployed control-plane surfaces from one `/actiongraph` base URL, zero-dependency `ActionGraphControlPlaneHttpClientProperties` for building that aggregate client from `.properties` / configuration-center keys, optional safe GET retries for transient read-side failures, zero-dependency `ActionGraphRuntimeHttpClient` for calling `/interpret`, `/runs`, and `/runs/{runId}/resume`, zero-dependency `ActionGraphComponentCatalogHttpClient` for inspecting deployed component catalog endpoints, zero-dependency `ActionGraphHumanReviewHttpClient` for querying/deciding human-review tasks and callbacks, zero-dependency `ActionGraphConsoleHttpClient` for read-only run/trace queries and CSV/JSONL audit exports, or shared-secret token verification without depending on Spring Web. Built-in Spring endpoints and Console endpoints reuse it transitively. GET retries are opt-in and limited to catalog/review-task/console reads; POST calls that can create side effects are not retried automatically. The token helper validates configured header names, skips token lookup when no shared secret is configured, and uses constant-time token comparison; it is not an enterprise IAM or RBAC layer. Runtime request headers whitelisted by the server are stored as trace metadata and copied to human-review task attributes when a high-risk run suspends, so legacy transaction ids and source-system ids remain visible through the approval path. The runtime API endpoint hard-excludes its configured token header from that metadata capture.

The component catalog exposes a `compatibility` label for each module. Today `actiongraph-control-plane-api` is the Java 8 client artifact that Java 8 applications can import directly; embeddable runtime, Spring, JDBC, governance, LLM, Console, and sample modules are `java21-plus` or `sample-only` and should run on the modern ActionGraph service side behind HTTP, an enterprise gateway, ESB, or a Java 8+ sidecar.

Non-Spring services can use `actiongraph-human-review` directly when they need external approval task storage, callback handling, multi-stage approval chains, or a stable task query/decision service without Spring MVC.

When `actiongraph-spring-boot-starter` is on the classpath, governance auto-configuration is available. Masking is activated with `actiongraph.masking.enabled=true`; amount-limit rules are activated by configuring `actiongraph.limits.rules`; review attributes and risk-based approval routing use the same starter and remain controlled by `actiongraph.human-review.risk-based-approval-chain`.

When `actiongraph-spring-boot-starter` is on the classpath, Spring services get repository-backed human review defaults. The starter supplies an in-memory `HumanReviewRepository` by default, and supplies a durable JDBC `HumanReviewRepository` when `actiongraph.persistence.jdbc.enabled=true` and a `DataSource` is available. In Spring MVC applications, pending-task/run-task/detail/decision endpoints and external approval callback endpoints are still opt-in through their own `actiongraph.human-review.*.enabled=true` switches.

```text
Human Review API: GET  /actiongraph/human-review/tasks/pending
Human Review API: GET  /actiongraph/human-review/tasks/runs/{runId}
Human Review API: GET  /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}
Human Review API: POST /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}/decision
Human Review Callback: POST /actiongraph/human-review/callbacks
```

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

`actiongraph-console` can be used directly by custom monitoring services, CLIs, or enterprise gateways that want the run query service, response model, `ConsoleRunRepository` port, JDBC trace read model, and CSV/JSONL audit export service without Spring MVC endpoints. Spring MVC control-plane services add `actiongraph-console-spring-boot-starter` when they want the built-in JSON API, HTML page, export endpoints, and optional JDBC `ConsoleRunRepository` auto-configuration from a `DataSource`. With `actiongraph.console.enabled=true`, the read-only surface is:

If a single Spring MVC deployment should expose the built-in runtime entry, component catalog, approval task, approval callback, and console endpoints together, add `actiongraph-spring-boot-starter` plus the optional `actiongraph-console-spring-boot-starter`, then enable only the endpoint properties that deployment owns. The starters still do not create business actions, LLM clients, or domain-specific interpreters for you.

```text
Console starter: GET /actiongraph/console
Console starter: GET /actiongraph/console/runs?limit=50&offset=0&status=COMPLETED&auditComplete=true
Console starter: GET /actiongraph/console/runs/{runId}
Console starter: GET /actiongraph/console/runs/{runId}/trace
Console starter: GET /actiongraph/console/runs/export.csv
Console starter: GET /actiongraph/console/runs/{runId}/trace/export.csv
Console starter: GET /actiongraph/console/runs/{runId}/trace/export.jsonl
```

The built-in page provides a run list, filters, selected-run metadata, and a trace timeline. API responses include paging metadata, run status, first/last trace timestamps, trace event count, trace details, and trace-chain verification results. Export responses provide attachment-friendly CSV or JSONL audit evidence over the same read-only service. Configure `actiongraph.console.shared-secret` to require the console token header for API and export calls. UI-only deployments can pair the page with a custom API gateway; API-only or export-only deployments can serve enterprise control-plane frontends without exposing the bundled page.

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
Batch reports include Markdown, CSV, and a read-only HTML console with total runtime, business action time, framework overhead, and review wait time for each case. The `suspend-resume`, `external-decisions`, and `external-callbacks` review modes use the real suspended-run resume path and derive approval latency from review task timestamps. Production approval integrations can write decisions through `HumanReviewCallbackHandler` from `actiongraph-human-review` or enable the optional Spring Boot callback endpoint from `actiongraph-spring-boot-starter`.
The `external-callbacks` mode replays JSONL approval callback deliveries through `HumanReviewCallbackHandler`, including shared-secret checks and duplicate-delivery idempotency.

## Documentation

- [Quick start guide](docs/quick-start.html)
- [Real LLM smoke test](docs/frameworkization/llm-smoke.md)
- [Human review integration](docs/frameworkization/human-review.md)
- [Runtime API](docs/frameworkization/runtime-api.md)
- [Component catalog](docs/frameworkization/component-catalog.md)
- [Control-plane API contracts](docs/frameworkization/control-plane-api.md)
- [Java 8 legacy integration](docs/frameworkization/java8-legacy-integration.md)
- [Java 8 Maven consumer example](docs/examples/java8-maven-consumer)
- [Java 8 component catalog client example](docs/examples/java8-component-catalog-client)
- [Java 8 aggregate control-plane client example](docs/examples/java8-control-plane-client)
- [Java 8 catalog HTTP client example](docs/examples/java8-catalog-http-client)
- [Java 8 human-review HTTP client example](docs/examples/java8-human-review-client)
- [Java 8 console HTTP client example](docs/examples/java8-console-client)
- [Java 8 legacy client example](docs/examples/java8-legacy-client)
- [Raw HTTP gateway contract reference](docs/examples/raw-http-gateway-contract)
- [Control-plane starter](docs/frameworkization/control-plane-starter.md)
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
