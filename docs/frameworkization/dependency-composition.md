# Dependency Composition

ActionGraph modules are intentionally small and independently usable. Import the BOM once, then select only the components a service actually needs.

## Always Start With The BOM

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
}
```

The BOM aligns versions for every published ActionGraph module. It has no runtime classes and does not pull any module by itself.

## Pure Java Runtime

Use this for a non-Spring service or library that wires actions and repositories manually.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
}
```

## Pure Java Annotation Adapter

Use this when a non-Spring service wants to register ordinary Java methods as Actions without implementing the `Action` interface.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
    implementation("com.actiongraph:actiongraph-annotations")
}
```

`actiongraph-annotations` depends only on `actiongraph-core`. It provides `@ActionGraphAction`, `@ActionGraphGuard`, `@ActionGraphCompensation`, `@BlackboardValue`, and `AnnotatedActionFactory`.

## Structured Memory Context

Use this when a service wants ActionGraph structured long-term memory without adopting Spring, JDBC, LLM, or governance modules.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
    implementation("com.actiongraph:actiongraph-memory")
}
```

`actiongraph-memory` depends only on `actiongraph-core`. It provides `MemoryScope`, `MemoryRecord`, `MemoryRepository`, `InMemoryMemoryRepository`, `MemoryContext`, and `MemoryContextLoader`.

## Goal Interpretation Contracts

Use this when a service wants GoalCatalog metadata, rule-based goal interpreters, or Goal-to-Blackboard seeding without an LLM provider.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
    implementation("com.actiongraph:actiongraph-interpretation")
}
```

`actiongraph-interpretation` depends only on `actiongraph-core`. It provides `GoalCatalog`, `GoalDefinition`, `GoalInterpreter`, `GoalInterpretation`, `GoalParameters`, `GoalBlackboardSeeder`, and `GoalBlackboardSeederRegistry`.

## Custom Runtime Entry API

Use this when a gateway, CLI, or custom controller wants a stable service for goal interpretation, run start, and suspended-run resume without adopting Spring MVC endpoints.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-runtime-api")
}
```

`actiongraph-runtime-api` wraps `GoalInterpreter`, `GoalBlackboardSeederRegistry`, `GoapExecutor`, and `ActionRegistry` with `ActionGraphRuntimeApiService` plus stable response DTOs. It does not provide an LLM provider, create repositories, register actions, or expose HTTP endpoints.

## Spring Business Runtime

Use this for a business service that executes ActionGraph runs and registers ordinary Spring bean methods as actions.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

The Spring starter brings `actiongraph-annotations` transitively and scans Spring beans for those annotations. It does not bring structured memory, repository-backed review tasks, JDBC repositories, or HTTP control-plane endpoints.

## Spring MVC Runtime Entry API

Use this when a Spring MVC service wants only goal interpretation, run start, and suspended-run resume endpoints. Business actions, `GoalInterpreter`, and `GoalBlackboardSeederRegistry` remain application-provided components.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-runtime-api-spring-boot-starter")
}
```

The Runtime API starter exposes only:

```text
POST /actiongraph/runtime/interpret
POST /actiongraph/runtime/runs
POST /actiongraph/runtime/runs/{runId}/resume
```

It requires `actiongraph.runtime.api.enabled=true`, a servlet web application, `GoalInterpreter`, `GoalBlackboardSeederRegistry`, `GoapExecutor`, and `ActionRegistry` beans. It does not create LLM clients, expose human-review task/callback endpoints, or expose Console endpoints.

## Component Catalog

Use this when a CLI, deployment check, gateway, or custom control-plane wants a machine-readable list of ActionGraph modules, capability tags, dependency hints, and recommended composition profiles.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-component-catalog")
}
```

`actiongraph-component-catalog` has no Spring, JDBC, LLM, or runtime dependency. It provides `ActionGraphComponentCatalogService`, component records, and composition profile records that can be reused outside HTTP.

## Control-Plane API Contracts

Use this when a custom gateway, endpoint adapter, or Java 8 legacy application wants ActionGraph's standard control-plane contract without depending on Spring MVC.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-api")
}
```

`actiongraph-control-plane-api` provides `ControlPlaneErrorResponse`, standard error-code factories such as `unauthorized`, `badRequest`, `notFound`, `conflict`, and `notClaimable`, plus a zero-dependency `ActionGraphRuntimeHttpClient` for calling `/interpret`, `/runs`, and `/runs/{runId}/resume`. It is compiled with `--release 8` and has no Spring, JDBC, LLM, runtime, auth, or JSON-library dependency. Built-in Spring MVC endpoint starters bring it transitively.

## Control-Plane Shared Auth

Use this when a custom gateway or endpoint adapter wants ActionGraph's shared-secret token semantics without depending on Spring MVC.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-auth")
}
```

`actiongraph-control-plane-auth` validates token-header configuration, treats blank shared secrets as disabled, and uses constant-time token comparison. It has no Spring, JDBC, LLM, or runtime dependency. Built-in Spring MVC endpoint starters bring it transitively, so applications usually add it directly only for custom control-plane adapters.

## Spring MVC Component Catalog API

Use this when a Spring MVC control-plane should expose the same component catalog as a read-only endpoint.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-component-catalog-spring-boot-starter")
}
```

The starter exposes only:

```text
GET /actiongraph/components
GET /actiongraph/components/modules
GET /actiongraph/components/modules/{module}
GET /actiongraph/components/profiles
GET /actiongraph/components/profiles/{profile}
```

It requires `actiongraph.component-catalog.enabled=true` and a servlet web application. It does not create runtime beans, repositories, LLM clients, action registries, review storage, approval callbacks, or console run repositories.

## Spring Structured Memory

Use this when a Spring Boot service wants in-memory structured memory defaults and `MemoryContextLoader`.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-memory-spring-boot-starter")
}
```

The memory starter brings `actiongraph-memory` transitively. It backs off if the application or `actiongraph-memory-jdbc-spring-boot-starter` provides a `MemoryRepository`.

## Repository-Backed Human Review

Use this when a non-Spring service, batch process, or approval integration service needs pending review tasks, multi-stage approval chains, or callback handling without exposing Spring MVC endpoints.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
    implementation("com.actiongraph:actiongraph-human-review")
}
```

`actiongraph-human-review` depends only on `actiongraph-core`. It provides `HumanReviewRepository`, `HumanReviewTask`, `InMemoryHumanReviewRepository`, `RepositoryBackedHumanReviewPolicy`, `HumanReviewCallbackHandler`, and approval-chain support.

## Durable Core Runtime

Add core JDBC repositories when traces and suspended runs must survive process restarts.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
}
```

Spring Boot applications still provide their own database driver and `DataSource`, then enable the repositories with:

```yaml
actiongraph:
  persistence:
    jdbc:
      enabled: true
      suspended-run-claim-timeout: 15m
      blackboard:
        allowed-packages:
          - com.example.business
```

Non-Spring services, or applications that want complete manual control, can depend on `actiongraph-persistence-jdbc` directly and instantiate the core repositories themselves.

## Durable Memory

Add JDBC memory only when structured memory must survive process restarts.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-memory-jdbc-spring-boot-starter")
}
```

Non-Spring services can depend on `actiongraph-memory-jdbc` directly and instantiate `JdbcMemoryRepository`.

## Durable Human Review

Add JDBC human-review tasks only when approval tasks and callback decisions must survive process restarts.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review-jdbc-spring-boot-starter")
}
```

Non-Spring services can depend on `actiongraph-human-review-jdbc` directly and instantiate `JdbcHumanReviewRepository`.

## Non-Spring Governance Policies

Use this when a non-Spring service wants ActionGraph's packaged masking, amount-limit, or rule-based permission policies without auto-configuration.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
    implementation("com.actiongraph:actiongraph-governance")
}
```

`actiongraph-governance` depends only on `actiongraph-core`. It provides reusable policy implementations such as `RegexMaskingPolicy`, `AmountLimitPolicy`, and `RuleBasedPermissionPolicy`, but it does not register actions, persist state, route approval chains, or expose endpoints.

## Non-Spring Human-Review Governance

Use this when a non-Spring service already uses `actiongraph-human-review` and wants amount review attributes or risk-based approval-chain routing.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-governance-human-review")
}
```

`actiongraph-governance-human-review` depends on `actiongraph-governance` and `actiongraph-human-review`. It provides `AmountAttributeContributor` and `RiskBasedChainResolver` without forcing those human-review contracts into the base governance library.

## Spring Boot Governance Policies

Add governance policies when a Spring Boot service needs data masking, amount-limit rules, or rule-based permission wiring.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
}
```

The governance starter wraps `actiongraph-governance` with Spring Boot auto-configuration. It uses the same `actiongraph.*` property namespace as the runtime starter, but it is the component that activates:

- `actiongraph.masking.*`
- `actiongraph.limits.*`

Without this module, the base Spring runtime remains neutral: no masking, default permission allow, no amount escalation, and safe pending human review.

## Spring Boot Human-Review Governance

Add this only when Spring Boot human-review flows should receive amount review attributes or risk-based approval-chain routing.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-human-review-spring-boot-starter")
}
```

The human-review governance starter activates `actiongraph.human-review.risk-based-approval-chain` and converts configured amount-limit review thresholds into `HumanReviewRequest.attributes`.

## Provider-Neutral Natural-Language Entry

Use this when a service wants ActionGraph's LLM goal interpreter, GoalCatalog prompt renderer, and structured output parser, but will provide its own `LlmClient`.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-llm")
}
```

`actiongraph-llm` depends on `actiongraph-core` and `actiongraph-interpretation`; it does not call any provider by itself.

## DeepSeek Natural-Language Entry

Add the DeepSeek-compatible adapter when the service needs LLM goal interpretation.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
}
```

`actiongraph-llm-deepseek` brings `actiongraph-llm` transitively and adds only the DeepSeek-compatible HTTP client. The LLM interpreter produces goals and parameters only; it does not generate plans or execute actions.

## Spring Repository-Backed Human Review

Use this when a Spring Boot service needs repository-backed review tasks and a `RepositoryBackedHumanReviewPolicy`.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
}
```

The human-review starter brings `actiongraph-human-review` transitively. It creates an in-memory `HumanReviewRepository`, a default `ApprovalChainResolver`, and `RepositoryBackedHumanReviewPolicy`; production services add `actiongraph-human-review-jdbc-spring-boot-starter` when review tasks and callback decisions must be durable. It does not expose HTTP endpoints.

## Spring MVC Human Review Callback Receiver

Use this in the same business service, or in a separate approval integration service, to receive external review decisions over HTTP.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-callback-spring-boot-starter")
}
```

The callback starter brings `actiongraph-human-review` transitively, but it does not create `HumanReviewRepository` or `HumanReviewPolicy` beans. It only creates `HumanReviewCallbackHandler` and the Spring MVC controller when `actiongraph.human-review.callback-endpoint.enabled=true`, a servlet web application is present, and a `HumanReviewRepository` bean already exists.

## Custom Human Review Task API

Use this for an approval inbox, CLI, or gateway adapter that wants stable human-review task query/decision DTOs without Spring MVC endpoints.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review-api")
}
```

`actiongraph-human-review-api` wraps `HumanReviewRepository` with `HumanReviewApiService`, pending/run/detail task projections, and stage-aware decision operations. It does not create review storage, execute, resume, compensate runs, or expose HTTP endpoints.

## Spring MVC Human Review Task API

Use this when a control-plane application wants only approval task query and decision endpoints without exposing the callback receiver.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review-api-spring-boot-starter")
}
```

The API starter exposes only:

```text
GET  /actiongraph/human-review/tasks/pending
GET  /actiongraph/human-review/tasks/runs/{runId}
GET  /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}
POST /actiongraph/human-review/tasks/runs/{runId}/actions/{actionId}/decision
```

It requires `actiongraph.human-review.api.enabled=true`, a servlet web application, and a `HumanReviewRepository` bean. It does not create review storage or expose `/actiongraph/human-review/callbacks`.

## Custom Read-Only Monitoring And Audit Export

Use this for a custom control-plane, CLI, gateway adapter, batch job, or audit archive process that wants read-only monitoring and export services without Spring MVC endpoints.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console")
}
```

`actiongraph-console` provides `ActionGraphConsoleService`, response records, paging validation, console page template rendering, the `ConsoleRunRepository` port, a JDBC adapter over `JdbcTraceRunRepository`, and CSV/JSONL audit export services. It remains read-only: it does not execute, resume, approve, deny, compensate runs, or expose HTTP endpoints.

## Spring MVC Read-Only Console

Use this for a Spring MVC control-plane application that wants the built-in Console page, JSON query API, downloadable audit evidence, and optional JDBC repository auto-configuration.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The Console starter wraps `actiongraph-console` with Spring MVC endpoints, token-header checks, shared service auto-configuration, and optional `DataSource` to `ConsoleRunRepository` wiring. It exposes only read-only endpoints:

```text
GET /actiongraph/console
GET /actiongraph/console/runs
GET /actiongraph/console/runs/{runId}
GET /actiongraph/console/runs/{runId}/trace
GET /actiongraph/console/runs/export.csv
GET /actiongraph/console/runs/{runId}/trace/export.csv
GET /actiongraph/console/runs/{runId}/trace/export.jsonl
```

It requires `actiongraph.console.enabled=true` and either an `ActionGraphConsoleService`/`ConsoleRunRepository` bean or an enabled JDBC repository auto-configuration with a `DataSource`. It must remain read-only: it does not execute, resume, approve, deny, or compensate runs.

## Spring MVC Control-Plane Aggregate

Use this convenience coordinate when one Spring MVC deployment should expose the built-in runtime entry API, component catalog API, approval task API, approval callback receiver, Console JSON API, Console UI, and Console export endpoints together.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-control-plane-spring-boot-starter")
}
```

The aggregate brings only endpoint starters. It does not create business runtime beans, interpreters, seeders, runtime repositories, review storage, LLM clients, or governance policies. Each endpoint family still requires its own `actiongraph.*.enabled=true` switch and, except the self-contained component catalog, its own backing beans. Console read-model repository wiring remains property-gated by the Console starter. Prefer split endpoint starters when the deployment should expose only part of the built-in control plane.

## Full Pilot Service

Use this for a single deployment that runs the business workflow, receives approval callbacks, persists state, interprets natural-language goals, and exposes the read-only Console.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-memory-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-memory-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-human-review-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-control-plane-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The same modules can later be split into separate runtime, approval, and monitoring services without changing their version alignment.
