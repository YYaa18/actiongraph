# Permission And Tenant Policy

v2 includes a reusable rule-based permission policy in `actiongraph-governance`. The core module keeps only the `PermissionPolicy` contract.

## Runtime Inputs

Applications seed the Blackboard with the current subject and target tenant scope:

```java
import com.actiongraph.governance.PermissionRule;
import com.actiongraph.governance.PolicySubject;
import com.actiongraph.governance.RuleBasedPermissionPolicy;
import com.actiongraph.governance.TenantScope;

blackboard.put(new PolicySubject(
        "user-123",
        "tenant-a",
        Set.of("sales"),
        Set.of("quote:create")
));

blackboard.put(new TenantScope("tenant-a"));
```

`PolicySubject` contains:

- user id
- current tenant id
- roles
- permissions

`TenantScope` represents the tenant of the resource being acted on.

## Rules

```java
PermissionRule createQuote = PermissionRule
        .forAction("quote.draft.create")
        .requireRole("sales")
        .requirePermission("quote:create")
        .requireTenantMatch()
        .build();

PermissionPolicy permissionPolicy =
        new RuleBasedPermissionPolicy(List.of(createQuote));

ExecutionPolicyGuard policyGuard =
        new DefaultPolicyGuard(permissionPolicy);
```

When `requireTenantMatch()` is set, execution is allowed only if:

```text
PolicySubject.tenantId == TenantScope.tenantId
```

## Default Behavior

`RuleBasedPermissionPolicy(rules)` allows actions without a matching rule. This keeps incremental adoption easy.

Use the second constructor for safe-by-default deployments:

```java
new RuleBasedPermissionPolicy(rules, false);
```

With `allowUnmatchedActions=false`, every executable action must have an explicit rule.

## Spring Boot

The base starter still defaults to `DefaultPermissionPolicy`, which allows all actions. Production applications can add `actiongraph-governance` or `actiongraph-governance-spring-boot-starter`, then override it:

```java
@Bean
PermissionPolicy permissionPolicy() {
    return new RuleBasedPermissionPolicy(List.of(
            PermissionRule.forAction("order.cancellation.request.draft")
                    .requireRole("support")
                    .requirePermission("order:cancel")
                    .requireTenantMatch()
                    .build()
    ), false);
}
```

Because the starter backs off when a `PermissionPolicy` bean exists, no other wiring is required.

## Semantics

- Missing subject denies actions that have a matching rule.
- Missing required role denies.
- Missing required permission denies.
- Missing or mismatched tenant scope denies when `requireTenantMatch()` is enabled.
- Policy denial returns `DENIED_BY_POLICY` and triggers compensation for already executed actions.
