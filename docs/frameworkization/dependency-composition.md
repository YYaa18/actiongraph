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

The Spring starter brings `actiongraph-annotations` transitively and scans Spring beans for those annotations. It does not expose HTTP control-plane endpoints.

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

## Durable Production Runtime

Add JDBC repositories when traces, suspended runs, review tasks, and memory must survive process restarts.

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

Non-Spring services, or applications that want complete manual control, can depend on `actiongraph-persistence-jdbc` directly and instantiate the repositories themselves.

## Non-Spring Governance Policies

Use this when a non-Spring service wants ActionGraph's packaged masking, amount-limit, approval-chain, or rule-based permission policies without auto-configuration.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-core")
    implementation("com.actiongraph:actiongraph-governance")
}
```

`actiongraph-governance` depends on `actiongraph-core` and `actiongraph-human-review`. It provides reusable policy implementations such as `RegexMaskingPolicy`, `AmountLimitPolicy`, `RiskBasedChainResolver`, and `RuleBasedPermissionPolicy`, but it does not register actions, persist state, or expose endpoints.

## Spring Boot Governance Policies

Add governance policies when a Spring Boot service needs data masking, amount-limit rules, or risk-based approval routing.

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
- `actiongraph.human-review.risk-based-approval-chain`

Without this module, the base Spring runtime remains neutral: no masking, default permission allow, no amount escalation, and single-stage review.

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

## Approval Callback Receiver

Use this in the same business service, or in a separate approval integration service, to receive external review decisions.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
}
```

The callback starter brings `actiongraph-human-review` transitively and requires a `HumanReviewRepository` bean. A business runtime service can get the in-memory default from `actiongraph-spring-boot-starter`; production services normally add `actiongraph-jdbc-spring-boot-starter` so the callback handler writes durable review decisions.

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

## Spring MVC Read-Only Monitoring Service

Use this for a separate control-plane application that only queries trace data.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The Console starter wraps `actiongraph-console-core` and `actiongraph-console-jdbc` with a Spring MVC controller, built-in static page, token-header check, and auto-configuration. It requires a `DataSource` and must remain read-only: it does not execute, resume, approve, deny, or compensate runs.

## Full Pilot Service

Use this for a single deployment that runs the business workflow, receives approval callbacks, persists state, interprets natural-language goals, and exposes the read-only Console.

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-governance-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-llm-deepseek")
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
    implementation("com.actiongraph:actiongraph-console-spring-boot-starter")
}
```

The same modules can later be split into separate runtime, approval, and monitoring services without changing their version alignment.
