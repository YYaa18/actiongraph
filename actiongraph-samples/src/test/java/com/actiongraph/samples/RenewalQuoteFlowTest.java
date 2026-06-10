package com.actiongraph.samples;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.RenewalActionFactory;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.RenewalGoals;
import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.domain.CurrentContract;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.service.ContractService;
import com.actiongraph.samples.renewal.service.InMemoryApprovalService;
import com.actiongraph.samples.renewal.service.InMemoryContractService;
import com.actiongraph.samples.renewal.service.InMemoryCustomerService;
import com.actiongraph.samples.renewal.service.InMemoryQuoteService;
import com.actiongraph.samples.renewal.service.InMemoryRenewalPolicyService;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class RenewalQuoteFlowTest {
    @Test
    void endToEndRenewalQuoteFlowProducesApprovalRequest() {
        FlowRun flowRun = runFlow();

        assertThat(flowRun.result().status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(flowRun.blackboard().conditions()).contains(RenewalConditions.SALES_APPROVAL_REQUESTED);
        assertThat(flowRun.blackboard().get(ApprovalRequest.class))
                .get()
                .extracting(ApprovalRequest::quoteId)
                .isEqualTo("QUOTE-1");
        assertThat(flowRun.traceRepository().findByRun(flowRun.result().runId()))
                .extracting(event -> event.type())
                .contains(TraceEventType.RUN_STARTED, TraceEventType.GOAL_SATISFIED, TraceEventType.RUN_ENDED);
    }

    @Test
    void sameInputProducesSameExecutedSequence() {
        List<List<String>> runs = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            runs.add(runFlow().result().executedActions().stream()
                    .map(ActionId::value)
                    .toList());
        }

        assertThat(runs).containsOnly(runs.getFirst());
    }

    @Test
    void missingCurrentContractReplansToSyntheticContractAction() {
        FlowRun flowRun = runFlow(new MissingContractService());

        assertThat(flowRun.result().status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(flowRun.result().executedActions())
                .extracting(ActionId::value)
                .contains("contract.synthetic.create")
                .doesNotContain("contract.current.query");
        assertThat(flowRun.blackboard().get(CurrentContract.class))
                .contains(new CurrentContract("SYNTHETIC-C001", new CustomerId("C001"), true));
        assertThat(flowRun.traceRepository().findByRun(flowRun.result().runId()))
                .filteredOn(event -> event.type() == TraceEventType.RUNTIME_GUARD_FAILED)
                .extracting(event -> event.actionId())
                .containsExactly("contract.current.query");
        assertThat(flowRun.traceRepository().findByRun(flowRun.result().runId()))
                .filteredOn(event -> event.type() == TraceEventType.PLAN_GENERATED)
                .extracting(event -> event.data().get("steps"))
                .anySatisfy(steps -> assertThat(steps).contains("contract.current.query"))
                .anySatisfy(steps -> assertThat(steps).contains("contract.synthetic.create"));
    }

    private FlowRun runFlow() {
        return runFlow(new InMemoryContractService());
    }

    private FlowRun runFlow(ContractService contractService) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new CustomerId("C001"));
        blackboard.addCondition(RenewalConditions.CUSTOMER_ID_PRESENT);

        List<Action> actions = RenewalActionFactory.actions(
                new InMemoryCustomerService(),
                contractService,
                new InMemoryRenewalPolicyService(),
                new InMemoryQuoteService(),
                new InMemoryApprovalService()
        );
        DefaultActionRegistry registry = RenewalActionFactory.registry(actions);
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                traceRepository
        );
        RunResult result = executor.run(RenewalGoals.prepareRenewalQuote(), blackboard, actions, registry);
        return new FlowRun(blackboard, traceRepository, result);
    }

    private record FlowRun(
            InMemoryBlackboard blackboard,
            InMemoryTraceRepository traceRepository,
            RunResult result
    ) {
    }

    private static final class MissingContractService implements ContractService {
        @Override
        public boolean hasCurrent(CustomerId customerId) {
            return false;
        }

        @Override
        public CurrentContract findCurrent(CustomerId customerId) {
            throw new IllegalStateException("No current contract for " + customerId.value());
        }
    }
}
