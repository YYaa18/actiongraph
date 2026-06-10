package com.actiongraph.planning;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.renewal.RenewalActionFactory;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.RenewalGoals;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoapPlannerTest {
    @Test
    void returnsEmptyPlanWhenGoalAlreadySatisfied() {
        Condition done = Condition.of("DONE");
        Goal goal = new Goal("done", Set.of(done));

        assertThat(new GoapPlanner().plan(goal, Set.of(done), List.of()))
                .hasValueSatisfying(plan -> assertThat(plan.steps()).isEmpty());
    }

    @Test
    void findsDeterministicFiveStepRenewalPlan() {
        List<Action> actions = renewalActions();

        assertThat(new GoapPlanner().plan(
                RenewalGoals.prepareRenewalQuote(),
                Set.of(RenewalConditions.CUSTOMER_ID_PRESENT),
                actions
        )).hasValueSatisfying(plan -> assertThat(actionIds(plan)).containsExactly(
                "contract.current.query",
                "customer.profile.query",
                "renewal.eligibility.check",
                "quote.draft.create",
                "sales.approval.request"
        ));
    }

    @Test
    void returnsEmptyWhenRequiredActionIsMissing() {
        List<Action> withoutApproval = renewalActions().stream()
                .filter(action -> !action.id().value().equals("sales.approval.request"))
                .toList();

        assertThat(new GoapPlanner().plan(
                RenewalGoals.prepareRenewalQuote(),
                Set.of(RenewalConditions.CUSTOMER_ID_PRESENT),
                withoutApproval
        )).isEmpty();
    }

    @Test
    void respectsMaxDepthAndDoesNotLoopOnVisitedStates() {
        Condition start = Condition.of("START");
        Condition middle = Condition.of("MIDDLE");
        Condition done = Condition.of("DONE");
        List<Action> actions = List.of(
                action("a.loop", Set.of(start), Set.of(start)),
                action("b.middle", Set.of(start), Set.of(middle)),
                action("c.done", Set.of(middle), Set.of(done))
        );

        assertThat(new GoapPlanner(1, 100).plan(
                new Goal("done", Set.of(done)),
                Set.of(start),
                actions
        )).isEmpty();

        assertThat(new GoapPlanner(2, 100).plan(
                new Goal("done", Set.of(done)),
                Set.of(start),
                actions
        )).hasValueSatisfying(plan -> assertThat(actionIds(plan)).containsExactly("b.middle", "c.done"));
    }

    private List<String> actionIds(Plan plan) {
        return plan.steps().stream()
                .map(step -> step.actionId().value())
                .toList();
    }

    private List<Action> renewalActions() {
        return RenewalActionFactory.actions(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(),
                new InMemoryQuoteService(),
                new InMemoryApprovalService()
        );
    }

    private Action action(String id, Set<Condition> preconditions, Set<Condition> effects) {
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
                return preconditions;
            }

            @Override
            public Set<Condition> effects() {
                return effects;
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
