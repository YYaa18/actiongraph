# Spring Boot Starter

`actiongraph-spring-boot-starter` lets application code register runtime actions with annotations on ordinary Spring beans. It brings `actiongraph-annotations` transitively and scans container beans, so business classes do not need to implement `Action` or build an `ActionRegistry` manually. It intentionally does not bring structured memory, repository-backed review tasks, JDBC persistence, or HTTP control-plane endpoints; those are separate optional ecosystem components.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.1.0"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

## Bean Registration

The starter auto-configures:

- `Planner` backed by `GoapPlanner`
- `TraceRepository` backed by `InMemoryTraceRepository`
- `SuspendedRunRepository` backed by `InMemorySuspendedRunRepository`
- neutral `PermissionPolicy`
- `ExecutionPolicyGuard`
- safe pending `HumanReviewPolicy`
- no-op `DataMaskingPolicy`
- no-op `ReviewAttributeContributor`
- `Executor` backed by `GoapExecutor`
- `ActionRegistry`

The generated `ActionRegistry` includes:

- every Spring `Action` bean
- every bean method annotated with `@ActionGraphAction`
- matching `@ActionGraphGuard` and `@ActionGraphCompensation` methods on the same annotated target set

If the application provides its own bean of the same type, the auto-configured default backs off.

## Example

```java
@Service
class OrderCancellationWorkflow {
    @ActionGraphAction(
            id = "order.lookup",
            preconditions = "order-cancellation:ORDER_ID_PRESENT",
            effects = "order-cancellation:ORDER_LOADED"
    )
    OrderRecord lookup(OrderId orderId) {
        return orderService.lookup(orderId);
    }

    @ActionGraphGuard(actionId = "order.cancellation.request.draft")
    boolean canDraft(CancellationEligibility eligibility) {
        return eligibility.allowed();
    }

    @ActionGraphCompensation(actionId = "order.cancellation.request.draft")
    void voidDraft(CancellationRequestDraft draft) {
        cancellationRequestService.voidDraft(draft.requestId());
    }
}
```

## Properties

```yaml
actiongraph:
  planner:
    max-depth: 32
    max-expansions: 10000
  executor:
    max-steps: 64
  actions:
    auto-register-annotated: true
```

Set `actiongraph.actions.auto-register-annotated=false` to build an `ActionRegistry` only from explicit Spring `Action` beans.

## Optional Ecosystem Components

This starter intentionally does not include durable persistence, structured memory defaults, repository-backed review tasks, optional governance policies, or HTTP control-plane endpoints. Add `actiongraph-memory-spring-boot-starter` for structured memory defaults; `actiongraph-runtime-api` or `actiongraph-runtime-api-spring-boot-starter` for reusable interpretation/start/resume services or endpoints; `actiongraph-component-catalog` or `actiongraph-component-catalog-spring-boot-starter` for module metadata; `actiongraph-control-plane-api` for Java 8 compatible response contracts and the lightweight Runtime API HTTP client; `actiongraph-control-plane-auth` for shared-secret token checks; `actiongraph-human-review` for non-Spring review tasks and task query/decision DTOs; `actiongraph-human-review-spring-boot-starter` for repository-backed review tasks and optional JDBC review storage; `actiongraph-human-review-api-spring-boot-starter` for human-review task query/decision and callback HTTP endpoints; governance starters for packaged masking, limits, and review routing; JDBC starters for durable trace/suspend and memory repositories; `actiongraph-console` or `actiongraph-console-spring-boot-starter` for read-only monitoring/export; or `actiongraph-control-plane-spring-boot-starter` when one deployment should expose the built-in runtime, component catalog, human-review, callback, and Console endpoints together. Keeping these separate lets services use ActionGraph runtime integration without pulling in infrastructure, governance, review storage, memory, or endpoint surfaces they do not need.

## Current Scope

This starter intentionally keeps only runtime trace/suspend persistence in-memory and policy defaults neutral/simple. Production applications should add the optional governance/JDBC/human-review/memory starters or replace `TraceRepository`, `SuspendedRunRepository`, `PermissionPolicy`, and `HumanReviewPolicy` with application-specific beans.

For packaged governance policies, add `actiongraph-governance` in non-Spring services or `actiongraph-governance-spring-boot-starter` in Spring Boot services. The base governance starter activates the `actiongraph.masking.*` and `actiongraph.limits.*` configuration trees. Add `actiongraph-governance-human-review` or `actiongraph-governance-human-review-spring-boot-starter` when those limits should enrich review requests or when `actiongraph.human-review.risk-based-approval-chain` should be active. Without those modules, these properties are intentionally ignored by the base runtime starter.

For rule-based permissions and tenant checks:

```java
import com.actiongraph.governance.PermissionRule;
import com.actiongraph.governance.RuleBasedPermissionPolicy;

@Bean
PermissionPolicy permissionPolicy() {
    return new RuleBasedPermissionPolicy(List.of(
            PermissionRule.forAction("quote.draft.create")
                    .requireRole("sales")
                    .requirePermission("quote:create")
                    .requireTenantMatch()
                    .build()
    ), false);
}
```

For Spring Boot JDBC persistence, add `actiongraph-jdbc-spring-boot-starter` and enable it:

```kotlin
dependencies {
    implementation("com.actiongraph:actiongraph-jdbc-spring-boot-starter")
}
```

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

The core JDBC starter creates `TraceRepository`, `SuspendedRunRepository`, and the trace run read model when a `DataSource` is available. If the application defines any of those beans itself, the auto-configured default backs off.

For Spring Boot structured memory, add `actiongraph-memory-spring-boot-starter`:

```kotlin
dependencies {
    implementation("com.actiongraph:actiongraph-memory-spring-boot-starter")
}
```

It creates an in-memory `MemoryRepository` and `MemoryContextLoader`. If `actiongraph.persistence.jdbc.enabled=true` and a `DataSource` is available, the starter backs off to the JDBC `MemoryRepository`.

For Spring Boot repository-backed human review, add `actiongraph-human-review-spring-boot-starter`:

```kotlin
dependencies {
    implementation("com.actiongraph:actiongraph-human-review-spring-boot-starter")
}
```

It creates an in-memory `HumanReviewRepository`, a default `ApprovalChainResolver`, and `RepositoryBackedHumanReviewPolicy`. If `actiongraph.persistence.jdbc.enabled=true` and a `DataSource` is available, the starter backs off to the JDBC `HumanReviewRepository`. Enable task query/decision endpoints or the callback endpoint separately by adding `actiongraph-human-review-api-spring-boot-starter` and setting `actiongraph.human-review.api.enabled=true` and/or `actiongraph.human-review.callback-endpoint.enabled=true`.

For non-Spring services or fully manual wiring, add only the JDBC modules needed and expose repository beans:

```java
@Bean
TraceRepository traceRepository(DataSource dataSource) {
    return new JdbcTraceRepository(dataSource);
}

@Bean
SuspendedRunRepository suspendedRunRepository(DataSource dataSource) {
    BlackboardTypeRegistry blackboardTypes = BlackboardTypeRegistry.builder()
            .allowPackage("com.example.business")
            .build();
    return new JdbcSuspendedRunRepository(dataSource, blackboardTypes);
}

@Bean
HumanReviewRepository humanReviewRepository(DataSource dataSource) {
    return new JdbcHumanReviewRepository(dataSource);
}

@Bean
MemoryRepository memoryRepository(DataSource dataSource) {
    return new JdbcMemoryRepository(dataSource);
}
```
