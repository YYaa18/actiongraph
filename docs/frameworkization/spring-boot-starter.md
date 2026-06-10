# Spring Boot Starter

`actiongraph-spring-boot-starter` lets application code register runtime actions with annotations on ordinary Spring beans. It brings `actiongraph-annotations` transitively and scans container beans, so business classes do not need to implement `Action` or build an `ActionRegistry` manually.

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
- single-stage `HumanReviewPolicy`
- `HumanReviewRepository`
- no-op `DataMaskingPolicy`
- no-op `ReviewAttributeContributor`
- `MemoryRepository`
- `MemoryContextLoader`
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

This starter intentionally does not include durable persistence, optional governance policies, or HTTP control-plane endpoints. Add `actiongraph-governance-spring-boot-starter` when a Spring Boot application needs masking, amount limits, or risk-based approval routing; add `actiongraph-jdbc-spring-boot-starter` when it needs JDBC-backed repositories; add `actiongraph-human-review-spring-boot-starter` when it needs approval callbacks; add `actiongraph-console-core` for custom read-only monitoring integrations; add `actiongraph-console-jdbc` when that custom monitor should read JDBC trace tables; and add `actiongraph-console-spring-boot-starter` when it needs the built-in Spring MVC operational run monitoring endpoint/page. Keeping these separate lets services use ActionGraph runtime integration without pulling in infrastructure, governance, or endpoint surfaces they do not need.

## Current Scope

This starter intentionally keeps persistence in-memory and policy defaults neutral/simple. Production applications should add the optional governance/JDBC starters or replace `TraceRepository`, `SuspendedRunRepository`, `PermissionPolicy`, and `HumanReviewPolicy` with application-specific beans.

For packaged governance policies, add `actiongraph-governance` in non-Spring services or `actiongraph-governance-spring-boot-starter` in Spring Boot services. The starter activates the `actiongraph.masking.*`, `actiongraph.limits.*`, and `actiongraph.human-review.risk-based-approval-chain` configuration trees. Without that module, those properties are intentionally ignored by the base runtime starter.

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

The JDBC starter creates `TraceRepository`, `SuspendedRunRepository`, `HumanReviewRepository`, `MemoryRepository`, and the console read model when a `DataSource` is available. If the application defines any of those beans itself, the auto-configured default backs off.

For non-Spring services or fully manual wiring, add `actiongraph-persistence-jdbc` and expose repository beans:

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
