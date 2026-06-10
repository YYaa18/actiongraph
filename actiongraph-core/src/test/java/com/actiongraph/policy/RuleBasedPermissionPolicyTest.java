package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RuleBasedPermissionPolicyTest {
    @Test
    void allowsWhenRolePermissionAndTenantMatch() {
        RuleBasedPermissionPolicy policy = new RuleBasedPermissionPolicy(List.of(
                PermissionRule.forAction("quote.draft.create")
                        .requireRole("sales")
                        .requirePermission("quote:create")
                        .requireTenantMatch()
                        .build()
        ));
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PolicySubject(
                "u1",
                "tenant-a",
                Set.of("sales"),
                Set.of("quote:create")
        ));
        blackboard.put(new TenantScope("tenant-a"));

        assertThat(policy.canExecute(action("quote.draft.create"), blackboard)).isTrue();
    }

    @Test
    void deniesWhenSubjectIsMissingRolePermissionOrTenant() {
        RuleBasedPermissionPolicy policy = new RuleBasedPermissionPolicy(List.of(
                PermissionRule.forAction("quote.draft.create")
                        .requireRole("sales")
                        .requirePermission("quote:create")
                        .requireTenantMatch()
                        .build()
        ));

        InMemoryBlackboard missingSubject = new InMemoryBlackboard();
        missingSubject.put(new TenantScope("tenant-a"));
        assertThat(policy.canExecute(action("quote.draft.create"), missingSubject)).isFalse();

        InMemoryBlackboard missingPermission = new InMemoryBlackboard();
        missingPermission.put(new PolicySubject("u1", "tenant-a", Set.of("sales"), Set.of()));
        missingPermission.put(new TenantScope("tenant-a"));
        assertThat(policy.canExecute(action("quote.draft.create"), missingPermission)).isFalse();

        InMemoryBlackboard tenantMismatch = new InMemoryBlackboard();
        tenantMismatch.put(new PolicySubject("u1", "tenant-a", Set.of("sales"), Set.of("quote:create")));
        tenantMismatch.put(new TenantScope("tenant-b"));
        assertThat(policy.canExecute(action("quote.draft.create"), tenantMismatch)).isFalse();
    }

    @Test
    void canDenyUnmatchedActionsWhenConfigured() {
        RuleBasedPermissionPolicy policy = new RuleBasedPermissionPolicy(List.of(), false);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PolicySubject("u1", "tenant-a", Set.of(), Set.of()));

        assertThat(policy.canExecute(action("unknown.action"), blackboard)).isFalse();
    }

    @Test
    void rejectsDuplicateRules() {
        PermissionRule rule = PermissionRule.forAction("quote.draft.create").build();

        assertThatThrownBy(() -> new RuleBasedPermissionPolicy(List.of(rule, rule)))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Duplicate permission rule");
    }

    private Action action(String id) {
        return new Action() {
            @Override
            public ActionId id() {
                return new ActionId(id);
            }

            @Override
            public Set<Class<?>> inputTypes() {
                return Set.of();
            }

            @Override
            public Set<Class<?>> outputTypes() {
                return Set.of();
            }

            @Override
            public Set<Condition> preconditions() {
                return Set.of();
            }

            @Override
            public Set<Condition> effects() {
                return Set.of();
            }

            @Override
            public int cost() {
                return 1;
            }

            @Override
            public ActionRiskLevel riskLevel() {
                return ActionRiskLevel.LOW;
            }

            @Override
            public boolean requiresHumanReview() {
                return false;
            }

            @Override
            public ActionResult execute(ExecutionContext context) {
                return ActionResult.ok();
            }
        };
    }
}
