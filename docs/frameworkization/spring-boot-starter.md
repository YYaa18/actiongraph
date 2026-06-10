# Spring Boot Starter

`actiongraph-spring-boot-starter` lets application code register runtime actions with annotations on ordinary Spring beans. Business classes do not need to implement `Action` or build an `ActionRegistry` manually.

## Dependency

```kotlin
dependencies {
    implementation("com.actiongraph:actiongraph-spring-boot-starter:0.1.0")
}
```

## Bean Registration

The starter auto-configures:

- `Planner` backed by `GoapPlanner`
- `TraceRepository` backed by `InMemoryTraceRepository`
- `SuspendedRunRepository` backed by `InMemorySuspendedRunRepository`
- `PermissionPolicy`
- `ExecutionPolicyGuard`
- `HumanReviewPolicy`
- `HumanReviewRepository`
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
  console:
    enabled: false
    path: /actiongraph/console
    token-header: X-ActionGraph-Console-Token
    shared-secret: ${ACTIONGRAPH_CONSOLE_SECRET}
    default-limit: 50
    max-limit: 200
```

Set `actiongraph.actions.auto-register-annotated=false` to build an `ActionRegistry` only from explicit Spring `Action` beans.

## Read-Only Console Endpoint

When `actiongraph.console.enabled=true`, `actiongraph-persistence-jdbc` is on the runtime classpath, and the application has a `DataSource` bean, the starter exposes a read-only run monitoring API:

```text
GET /actiongraph/console/runs?limit=50&offset=0&status=COMPLETED&auditComplete=true
GET /actiongraph/console/runs/{runId}
GET /actiongraph/console/runs/{runId}/trace
```

The endpoint returns `TraceRunSummary`-shaped JSON with paging metadata, run status, first/last trace timestamps, event count, trace-chain verification, and trace event details for a selected run. Configure `actiongraph.console.shared-secret` to require the configured token header. The endpoint does not mutate runtime state and does not resume or approve runs.

## Current Scope

This starter intentionally keeps persistence and policy defaults in-memory/simple. Production applications should replace `TraceRepository`, `SuspendedRunRepository`, `PermissionPolicy`, and `HumanReviewPolicy` with application-specific beans.

For rule-based permissions and tenant checks:

```java
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

For JDBC persistence, add `actiongraph-persistence-jdbc` and expose repository beans:

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
