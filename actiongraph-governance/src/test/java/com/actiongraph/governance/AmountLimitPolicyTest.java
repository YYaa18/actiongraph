package com.actiongraph.governance;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.PermissionPolicy;
import com.actiongraph.policy.PolicyDecision;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AmountLimitPolicyTest {
    private static final AmountLimitRule RULE = new AmountLimitRule(
            "payment.transfer",
            "CNY",
            new BigDecimal("1000000"),
            new BigDecimal("100000")
    );

    @Test
    void allowsActionsWithoutAmount() {
        AmountLimitPolicy policy = new AmountLimitPolicy(
                (action, blackboard) -> Optional.empty(),
                List.of(RULE)
        );

        assertThat(policy.canExecute(action("payment.transfer"), new InMemoryBlackboard())).isTrue();
    }

    @Test
    void deniesWhenAmountCurrencyHasNoConfiguredLimit() {
        AmountLimitPolicy policy = new AmountLimitPolicy(
                amount("50000", "USD"),
                List.of(RULE)
        );

        assertThat(policy.canExecute(action("payment.transfer"), new InMemoryBlackboard())).isFalse();
    }

    @Test
    void deniesAboveHardLimitAndAllowsAtHardLimit() {
        Action action = action("payment.transfer");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        AmountLimitPolicy aboveHard = new AmountLimitPolicy(amount("1000000.01", "CNY"), List.of(RULE));
        AmountLimitPolicy atHard = new AmountLimitPolicy(amount("1000000", "CNY"), List.of(RULE));

        assertThat(aboveHard.canExecute(action, blackboard)).isFalse();
        assertThat(atHard.canExecute(action, blackboard)).isTrue();
    }

    @Test
    void wildcardRuleAppliesWhenActionSpecificRuleIsAbsent() {
        AmountLimitRule wildcard = new AmountLimitRule(
                AmountLimitRule.ANY_ACTION,
                "CNY",
                new BigDecimal("100"),
                new BigDecimal("10")
        );
        AmountLimitPolicy policy = new AmountLimitPolicy(amount("99", "CNY"), List.of(wildcard));

        assertThat(policy.canExecute(action("unconfigured.action"), new InMemoryBlackboard())).isTrue();
    }

    @Test
    void defaultPolicyGuardEvaluatesMultiplePermissionPoliciesInOrder() {
        PermissionPolicy allow = new PermissionPolicy() {
            @Override
            public boolean canExecute(Action action, com.actiongraph.runtime.Blackboard blackboard) {
                return true;
            }
        };
        PermissionPolicy deny = new PermissionPolicy() {
            @Override
            public boolean canExecute(Action action, com.actiongraph.runtime.Blackboard blackboard) {
                return false;
            }
        };
        DefaultPolicyGuard guard = new DefaultPolicyGuard(allow, deny);

        assertThat(guard.evaluate(action("payment.transfer"), new InMemoryBlackboard()))
                .isEqualTo(PolicyDecision.DENY);
    }

    private AmountExtractor amount(String value, String currency) {
        return (action, blackboard) -> Optional.of(new MonetaryAmount(new BigDecimal(value), currency));
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
