package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

class PermissionPolicyPrincipalTest {
    private final Action action = new TestAction();
    private final Blackboard blackboard = new InMemoryBlackboard();

    @Test
    void legacyTwoArgumentPolicyStillParticipatesWhenPrincipalAwarePathIsUsed() {
        AtomicBoolean called = new AtomicBoolean(false);
        PermissionPolicy legacyPolicy = new PermissionPolicy() {
            @Override
            public boolean canExecute(Action action, Blackboard blackboard) {
                called.set(true);
                return false;
            }
        };

        PolicyDecision decision = new DefaultPolicyGuard(legacyPolicy)
                .evaluate(action, blackboard, RunPrincipal.of("user:alice"));

        assertThat(decision).isEqualTo(PolicyDecision.DENY);
        assertThat(called).isTrue();
    }

    @Test
    void principalAwarePolicyReceivesRunPrincipal() {
        AtomicReference<RunPrincipal> captured = new AtomicReference<>();
        PermissionPolicy principalPolicy = new PermissionPolicy() {
            @Override
            public boolean canExecute(Action action, Blackboard blackboard, RunPrincipal principal) {
                captured.set(principal);
                return true;
            }
        };
        RunPrincipal principal = new RunPrincipal("user:alice", "portal", List.of(),
                Map.of("roles", "maker"));

        PolicyDecision decision = new DefaultPolicyGuard(principalPolicy)
                .evaluate(action, blackboard, principal);

        assertThat(decision).isEqualTo(PolicyDecision.ALLOW);
        assertThat(captured).hasValue(principal);
    }

    @Test
    void rolePermissionPolicyAllowsAnyConfiguredRoleAndDeniesMissingRoles() {
        RolePermissionPolicy policy = new RolePermissionPolicy(Map.of(
                "secure.action", Set.of("checker", "supervisor")
        ));

        assertThat(policy.canExecute(action, blackboard,
                new RunPrincipal("user:alice", "portal", List.of(), Map.of("roles", "maker"))))
                .isFalse();
        assertThat(policy.canExecute(action, blackboard,
                new RunPrincipal("user:bob", "portal", List.of(), Map.of("roles", "maker,checker"))))
                .isTrue();
    }

    private static final class TestAction implements Action {
        @Override
        public ActionId id() {
            return new ActionId("secure.action");
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
            return Set.of(Condition.of("DONE"));
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

        @Override
        public CompensationResult compensate(ExecutionContext context) {
            return CompensationResult.noop();
        }
    }
}
