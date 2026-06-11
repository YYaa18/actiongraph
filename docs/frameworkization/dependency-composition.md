# Dependency Composition

ActionGraph modules are independently usable, but the public dependency surface is being consolidated. Import the BOM once, then choose a small number of components based on where the code runs.

## Always Start With The BOM

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
}
```

The BOM aligns versions for every published ActionGraph module. It has no runtime classes and does not pull any module by itself.

## Non-Spring Runtime

Use this for a service or library that wires actions, repositories, and entrypoints manually:

```kotlin
implementation("com.actiongraph:actiongraph-core")
```

`actiongraph-core` provides Action SPI, annotation-based action registration, planning, execution, policy, trace, compensation, suspend/resume, GoalCatalog metadata, GoalInterpreter contracts, Blackboard seeders, `ActionGraphRuntimeOperations` with the default `ActionGraphRuntimeApiService`, `BatchGoalInterpreter`, and structured memory contracts/defaults.

Add `actiongraph-persistence-jdbc` when a non-Spring service wants durable trace, suspended-run, trace read-model, memory, or human-review repositories.

## Spring Business Runtime

Use this for most Spring Boot business services:

```kotlin
implementation("com.actiongraph:actiongraph-spring-boot-starter")
```

The main starter brings:

- annotation-based Action scanning
- planner/executor/runtime defaults
- in-memory trace and suspended-run repositories
- structured memory defaults
- repository-backed human review defaults
- governance policy auto-configuration
- JDBC repository auto-configuration behind `actiongraph.persistence.jdbc.enabled=true`
- runtime, component-catalog, human-review task, and callback HTTP endpoints behind their own `enabled` switches
- a generic `LlmClient` only when `actiongraph.llm.provider` is explicitly set

It does not create business Actions or domain-specific interpreters. LLM provider wiring is opt-in and only creates a generic client; goal interpretation still needs application-owned goal metadata and interpreter beans.

## Java 8 Legacy Clients

Java 8 applications should not embed the modern runtime today. They should use the deployed ActionGraph service over HTTP through the Java 8 compatible control-plane artifact:

```kotlin
implementation("com.actiongraph:actiongraph-control-plane-api")
```

`actiongraph-control-plane-api` exposes module metadata, compatibility labels, composition profiles, response DTOs, aggregate and split HTTP clients, `.properties` based aggregate configuration, safe GET retries for read surfaces, audit/tracing headers, and shared-secret token verification. It has no Spring, JDBC, LLM, runtime, JSON-library, or third-party HTTP dependency.

## Human Review And Governance

Non-Spring services can use:

```kotlin
implementation("com.actiongraph:actiongraph-human-review")
implementation("com.actiongraph:actiongraph-governance")
```

Spring Boot services normally get both through `actiongraph-spring-boot-starter`. They remain controlled by configuration:

```yaml
actiongraph:
  masking:
    enabled: true
  limits:
    rules:
      - action-id: sales.approval.request
        currency: CNY
        hard-limit: 1000000
        review-limit: 100000
  human-review:
    risk-based-approval-chain: true
```

## Runtime And Review HTTP Endpoints

The main Spring starter includes these endpoint auto-configurations, all disabled by default:

```yaml
actiongraph:
  runtime:
    api:
      enabled: true
  component-catalog:
    enabled: true
  human-review:
    api:
      enabled: true
    callback-endpoint:
      enabled: true
```

Runtime endpoints are optional adapters over `ActionGraphRuntimeOperations`. If an application provides its own operations bean, the starter backs off from the default service and wires the controller to that interface. If the application wants the default service, it must provide `GoalInterpreter`, `GoalBlackboardSeederRegistry`, `GoapExecutor`, and `ActionRegistry` beans. Human-review endpoints require a `HumanReviewRepository`. Endpoint enablement never creates domain Actions or domain-specific interpreters.

Runtime start/resume endpoints support whitelisted request-header capture through `actiongraph.runtime.api.trace-headers`. Defaults are `X-Request-Id`, `X-Correlation-Id`, and `X-Source-System`; the configured runtime token header is hard-excluded from trace metadata.

## LLM Providers

Provider-neutral goal interpretation support and DeepSeek-compatible model access live in the optional LLM provider module:

```kotlin
implementation("com.actiongraph:actiongraph-llm-deepseek")
```

The LLM interpreter produces goals and parameters only. It does not generate plans or execute actions. Custom providers can implement `LlmClient` from this module while keeping the planner and executor dependency surface unchanged.

## Console

For custom monitoring services, CLIs, gateways, or audit exports without Spring MVC:

```kotlin
implementation("com.actiongraph:actiongraph-console")
```

For the built-in read-only Console API/UI/export endpoints:

```kotlin
implementation("com.actiongraph:actiongraph-spring-boot-starter")
```

The Console endpoints are property-gated and read-only. They can query runs/traces and export CSV/JSONL evidence, but they must not execute, resume, approve, deny, or compensate runs.

## Full Pilot Service

A single pilot deployment that runs business workflows, receives approval callbacks, persists state, interprets natural-language goals, and exposes the read-only Console usually needs only:

```kotlin
implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
implementation("com.actiongraph:actiongraph-spring-boot-starter")
implementation("com.actiongraph:actiongraph-llm-deepseek")
```

Turn on only the endpoint and persistence properties that deployment owns.
