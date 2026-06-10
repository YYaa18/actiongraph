package com.actiongraph.samples.ordercancellation;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.ordercancellation.actions.CancellationRequestDraftAction;
import com.actiongraph.samples.ordercancellation.domain.OperationsApprovalRequest;
import com.actiongraph.samples.ordercancellation.domain.OrderId;
import com.actiongraph.samples.ordercancellation.service.InMemoryCancellationPolicyService;
import com.actiongraph.samples.ordercancellation.service.InMemoryCancellationRequestService;
import com.actiongraph.samples.ordercancellation.service.InMemoryOperationsApprovalService;
import com.actiongraph.samples.ordercancellation.service.InMemoryOrderService;
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

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class OrderCancellationFlowTest {
    @Test
    void plannerFindsOrderCancellationPlan() {
        Fixture fixture = fixture(false);

        assertThat(new GoapPlanner().plan(
                OrderCancellationGoals.requestOrderCancellation(),
                fixture.blackboard().conditions(),
                fixture.actions()
        )).hasValueSatisfying(plan -> assertThat(plan.steps())
                .extracting(step -> step.actionId().value())
                .containsExactly(
                        "order.lookup",
                        "order.cancellation.eligibility.check",
                        "order.cancellation.request.draft",
                        "operations.approval.request"
                ));
    }

    @Test
    void eligibleOrderCompletesWhenHumanReviewIsApproved() {
        Fixture fixture = fixture(false);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(OrderCancellationGoals.requestOrderCancellation(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(fixture.blackboard().get(OperationsApprovalRequest.class)).isPresent();
        assertThat(fixture.traceRepository().findByRun(result.runId()))
                .extracting(event -> event.type())
                .contains(TraceEventType.HUMAN_REVIEW_REQUESTED, TraceEventType.HUMAN_REVIEW_DECIDED);
    }

    @Test
    void defaultHumanReviewPolicyStopsBeforeOperationsApproval() {
        Fixture fixture = fixture(false);
        GoapExecutor executor = new GoapExecutor(new GoapPlanner(), new DefaultPolicyGuard(), fixture.traceRepository());

        RunResult result = executor.run(OrderCancellationGoals.requestOrderCancellation(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(fixture.blackboard().get(OperationsApprovalRequest.class)).isEmpty();
        assertThat(result.executedActions()).extracting(actionId -> actionId.value())
                .containsExactly(
                        "order.lookup",
                        "order.cancellation.eligibility.check",
                        "order.cancellation.request.draft"
                );
    }

    @Test
    void shippedOrderFailsRuntimeGuardBeforeDraftingRequest() {
        Fixture fixture = fixture(true);
        GoapExecutor executor = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                fixture.traceRepository()
        );

        RunResult result = executor.run(OrderCancellationGoals.requestOrderCancellation(),
                fixture.blackboard(), fixture.actions(), fixture.registry());

        assertThat(result.status()).isEqualTo(RunStatus.HALTED_UNREACHABLE);
        assertThat(result.message()).contains(CancellationRequestDraftAction.ID.value());
        assertThat(fixture.requestService().drafts()).isEmpty();
    }

    private Fixture fixture(boolean shipped) {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new OrderId("O100"));
        blackboard.addCondition(OrderCancellationConditions.ORDER_ID_PRESENT);

        InMemoryCancellationRequestService requestService = new InMemoryCancellationRequestService();
        List<Action> actions = OrderCancellationActionFactory.actions(
                new InMemoryOrderService(shipped),
                new InMemoryCancellationPolicyService(),
                requestService,
                new InMemoryOperationsApprovalService()
        );
        return new Fixture(
                blackboard,
                actions,
                OrderCancellationActionFactory.registry(actions),
                requestService,
                new InMemoryTraceRepository()
        );
    }

    private record Fixture(
            InMemoryBlackboard blackboard,
            List<Action> actions,
            DefaultActionRegistry registry,
            InMemoryCancellationRequestService requestService,
            InMemoryTraceRepository traceRepository
    ) {
    }
}
