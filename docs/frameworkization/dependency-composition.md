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

## Spring Business Runtime

Use this for a business service that executes ActionGraph runs and registers ordinary Spring bean methods as actions.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

The Spring starter brings `actiongraph-annotations` transitively and scans Spring beans for those annotations. It does not bring structured memory, repository-backed review tasks, JDBC repositories, or HTTP control-plane endpoints.

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

## Custom Read-Only Monitoring Service

Use this for a custom control-plane, CLI, or gateway adapter that wants the console query service and response model without Spring MVC endpoints.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-core")
}
```

The console core has no JDBC or Spring Web dependency. It provides `ActionGraphConsoleService`, response records, paging validation, console page template rendering, and the `ConsoleRunRepository` port. Applications provide an implementation for their own read model.

## JDBC Read-Only Monitoring Adapter

Use this for a custom non-Spring monitoring service that reads ActionGraph trace tables directly.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-core")
    implementation("com.actiongraph:actiongraph-console-jdbc")
}
```

`actiongraph-console-jdbc` implements the `ConsoleRunRepository` port by adapting `JdbcTraceRunRepository`. It remains read-only and does not expose HTTP endpoints.

## Spring MVC Read-Only Monitoring API

Use this for a separate control-plane application that exposes read-only JSON endpoints over any `ConsoleRunRepository` without serving the bundled page.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-api-spring-boot-starter")
}
```

The Console API starter wraps `actiongraph-console-core` with Spring MVC JSON endpoints, token-header check, and shared service auto-configuration. It requires an `ActionGraphConsoleService` or `ConsoleRunRepository` bean and must remain read-only: it does not execute, resume, approve, deny, compensate runs, or serve the bundled page.

## Spring MVC Read-Only Monitoring UI

Use this when a control-plane application wants only the bundled HTML page, for example when JSON requests are routed through an enterprise gateway or a custom API service.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-ui-spring-boot-starter")
}
```

The Console UI starter serves only `GET /actiongraph/console` and injects the configured token header and paging limits into the page. It does not create repositories or expose `/runs` JSON endpoints.

## Spring MVC Read-Only Monitoring Aggregate

Use this compatibility coordinate when an application wants the built-in page and JSON API together through the previous single dependency.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The aggregate starter brings `actiongraph-console-api-spring-boot-starter` and `actiongraph-console-ui-spring-boot-starter` transitively. New applications should prefer the split dependencies when endpoint exposure needs to be explicit.

## Spring MVC JDBC Read-Only Monitoring Adapter

Use this when the Spring MVC control-plane should read ActionGraph JDBC trace tables directly.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-api-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-ui-spring-boot-starter")
}
```

`actiongraph-console-jdbc-spring-boot-starter` creates the `ConsoleRunRepository` bean from a `DataSource`. Keeping it separate lets Spring MVC control-plane services use a custom repository without pulling JDBC read-model code.

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
    implementation("com.actiongraph:actiongraph-human-review-callback-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-api-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-ui-spring-boot-starter")
}
```

The same modules can later be split into separate runtime, approval, and monitoring services without changing their version alignment.
