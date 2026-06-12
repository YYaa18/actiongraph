package com.actiongraph.samples.renewal;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalCatalog;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalTypes;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import com.actiongraph.trace.InMemoryTraceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RenewalContributionTest {
    @Test
    void contributionRegistersSeedersGoalsAndActionsForEndToEndRun() {
        RenewalContribution contribution = new RenewalContribution(
                new InMemoryCustomerService(),
                new InMemoryContractService(),
                new InMemoryRenewalPolicyService(),
                new InMemoryQuoteService(),
                new InMemoryApprovalService()
        );

        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        seeders.registerDefaultSeeders(RenewalGoalCatalog.create());
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        GoalInterpretation interpretation = GoalInterpretation.ready(
                RenewalGoalTypes.PREPARE_RENEWAL_QUOTE,
                GoalParameters.of(Map.of("customerId", "C001")),
                RenewalGoals.prepareRenewalQuote()
        );
        seeders.seed(interpretation, blackboard);

        List<Action> actions = contribution.actions();
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        var result = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                new InMemoryTraceRepository()
        ).run(contribution.goals().getFirst().goal(), blackboard, actions, registry);

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(blackboard.conditions()).contains(RenewalConditions.SALES_APPROVAL_REQUESTED);
        assertThat(blackboard.get(ApprovalRequest.class)).isPresent();
    }
}
