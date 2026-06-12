# Spring Boot Starter

`actiongraph-spring-boot-starter` is the primary Spring integration surface. It keeps adoption simple for business services: one dependency brings annotation-based Action registration, runtime defaults, structured memory, repository-backed human review, governance wiring, JDBC repository auto-configuration, and optional control-plane endpoints. Selection still happens through Spring beans and `actiongraph.*.enabled` properties.

## Dependency

```kotlin
dependencies {
    implementation(platform("com.actiongraph:actiongraph-bom:0.2.0-SNAPSHOT"))
    implementation("com.actiongraph:actiongraph-spring-boot-starter")
}
```

## Bean Registration

The starter auto-configures these defaults, all guarded by `@ConditionalOnMissingBean`:

- `Planner` backed by `GoapPlanner`
- `TraceRepository` backed by memory, or JDBC when enabled
- `SuspendedRunRepository` backed by memory, or JDBC when enabled
- `MemoryRepository` and `MemoryContextLoader`
- `HumanReviewRepository`, `ApprovalChainResolver`, and repository-backed `HumanReviewPolicy`
- `PermissionPolicy`, `ExecutionPolicyGuard`, `DataMaskingPolicy`, and `ReviewAttributeContributor`
- `Executor` backed by `GoapExecutor`
- `ActionRegistry`

The generated `ActionRegistry` includes every Spring `Action` bean and every bean method annotated with `@ActionGraphAction`, plus matching `@ActionGraphGuard` and `@ActionGraphCompensation` methods.

## Action Example

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

Set `actiongraph.actions.auto-register-annotated=false` to build an `ActionRegistry` only from explicit Spring `Action` beans.

## Persistence

By default, runtime trace, suspended-run snapshots, memory records, and human-review tasks use in-memory repositories. For production, keep the same starter and enable JDBC:

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

When a `DataSource` is available, the starter supplies JDBC-backed `TraceRepository`, `SuspendedRunRepository`, `JdbcTraceRunRepository`, `MemoryRepository`, and `HumanReviewRepository`. If the application defines any of those beans itself, the default backs off.

Non-Spring services or fully manual Spring services can depend on `actiongraph-persistence-jdbc` and instantiate the repositories directly.

## Governance

Non-Spring services can use `actiongraph-governance` directly. Spring Boot services get governance auto-configuration through the main starter:

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

Without matching configuration, the starter keeps neutral defaults: no masking, default allow policy, no amount escalation, and single-stage approval routing.

## HTTP Endpoints

The main starter contains the runtime entry, component catalog, human-review task, human-review callback, and Console endpoint auto-configurations. They are disabled by default and stay independently selectable:

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
  console:
    enabled: true
```

Each endpoint family keeps its own path, token header, shared-secret, and backing-bean requirements. Enabling an endpoint never creates business actions, LLM clients, or domain-specific interpreters.

## Console

The main starter exposes read-only run/trace APIs, the built-in HTML page, and CSV/JSONL export endpoints behind `actiongraph.console.*` properties. It must not execute, resume, approve, deny, or compensate runs.
