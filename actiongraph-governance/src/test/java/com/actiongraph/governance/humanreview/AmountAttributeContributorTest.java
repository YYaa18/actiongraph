package com.actiongraph.governance.humanreview;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.governance.AmountExtractor;
import com.actiongraph.governance.AmountLimitRule;
import com.actiongraph.governance.MonetaryAmount;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class AmountAttributeContributorTest {
    private static final AmountLimitRule RULE = new AmountLimitRule(
            "payment.transfer",
            "CNY",
            new BigDecimal("1000000"),
            new BigDecimal("100000")
    );

    @Test
    void marksReviewEscalationOnlyAboveReviewLimit() {
        Action action = action("payment.transfer");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        AmountAttributeContributor escalated = new AmountAttributeContributor(amount("100000.01", "CNY"), List.of(RULE));
        AmountAttributeContributor normal = new AmountAttributeContributor(amount("100000", "CNY"), List.of(RULE));

        assertThat(escalated.contribute(action, blackboard)).containsExactlyInAnyOrderEntriesOf(Map.of(
                "amount", "100000.01",
                "currency", "CNY",
                "amountEscalated", "true"
        ));
        assertThat(normal.contribute(action, blackboard)).isEmpty();
    }

    @Test
    void doesNotEscalateWhenAmountOrRuleIsAbsent() {
        Action action = action("payment.transfer");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        AmountAttributeContributor withoutAmount = new AmountAttributeContributor(
                (ignoredAction, ignoredBlackboard) -> Optional.empty(),
                List.of(RULE)
        );
        AmountAttributeContributor withoutRule = new AmountAttributeContributor(amount("100000.01", "USD"), List.of(RULE));

        assertThat(withoutAmount.contribute(action, blackboard)).isEmpty();
        assertThat(withoutRule.contribute(action, blackboard)).isEmpty();
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
