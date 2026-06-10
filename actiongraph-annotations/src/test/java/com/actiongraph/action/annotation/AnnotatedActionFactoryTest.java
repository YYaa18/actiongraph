package com.actiongraph.action.annotation;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.DenyingHumanReviewPolicy;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.BlackboardKey;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AnnotatedActionFactoryTest {
    private static final Condition ORDER_ID_PRESENT = Condition.of("order-cancellation", "ORDER_ID_PRESENT");
    private static final Condition OPERATIONS_APPROVAL_REQUESTED =
            Condition.of("order-cancellation", "OPERATIONS_APPROVAL_REQUESTED");
    private static final Goal REQUEST_ORDER_CANCELLATION =
            new Goal("requestOrderCancellation", Set.of(OPERATIONS_APPROVAL_REQUESTED));

    @Test
    void annotatedBusinessObjectRunsWithoutImplementingAction() {
        AnnotatedOrderCancellationAdapter adapter = new AnnotatedOrderCancellationAdapter(false);
        List<Action> actions = AnnotatedActionFactory.actions(adapter);
        InMemoryBlackboard blackboard = blackboard();
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

        var result = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                traceRepository
        ).run(REQUEST_ORDER_CANCELLATION,
                blackboard,
                actions,
                registry(actions));

        assertThat(Action.class.isAssignableFrom(adapter.getClass())).isFalse();
        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(blackboard.get(OperationsApprovalRequest.class))
                .contains(new OperationsApprovalRequest("OPS-APPROVAL-1", "CANCEL-1"));
        assertThat(result.executedActions()).extracting(actionId -> actionId.value())
                .containsExactly(
                        "order.lookup.annotated",
                        "order.cancellation.eligibility.check.annotated",
                        "order.cancellation.request.draft.annotated",
                        "operations.approval.request.annotated"
                );
    }

    @Test
    void annotatedRuntimeGuardCanHaltFlow() {
        AnnotatedOrderCancellationAdapter adapter = new AnnotatedOrderCancellationAdapter(true);
        List<Action> actions = AnnotatedActionFactory.actions(adapter);

        var result = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                new InMemoryTraceRepository()
        ).run(REQUEST_ORDER_CANCELLATION, blackboard(), actions, registry(actions));

        assertThat(result.status()).isEqualTo(RunStatus.HALTED_UNREACHABLE);
        assertThat(adapter.drafts()).isEmpty();
    }

    @Test
    void policyDenialCompensatesPreviouslySuccessfulAnnotatedActions() {
        AnnotatedOrderCancellationAdapter adapter = new AnnotatedOrderCancellationAdapter(false);
        List<Action> actions = AnnotatedActionFactory.actions(adapter);
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();

        var result = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new DenyingHumanReviewPolicy(),
                traceRepository
        ).run(REQUEST_ORDER_CANCELLATION, blackboard(), actions, registry(actions));

        assertThat(result.status()).isEqualTo(RunStatus.DENIED_BY_POLICY);
        assertThat(adapter.drafts()).containsExactly("CANCEL-1");
        assertThat(adapter.voidedDrafts()).containsExactly("CANCEL-1");
        assertThat(traceRepository.findByRun(result.runId()))
                .filteredOn(event -> event.type() == TraceEventType.COMPENSATED)
                .extracting(event -> event.actionId())
                .contains("order.cancellation.request.draft.annotated");
    }

    @Test
    void duplicateAnnotatedActionIdThrows() {
        assertThatThrownBy(() -> AnnotatedActionFactory.actions(new DuplicateActionAdapter()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("duplicate.id");
    }

    @Test
    void abstractOrInterfaceReturnTypeFailsFast() {
        assertThatThrownBy(() -> AnnotatedActionFactory.actions(new InterfaceReturnAdapter()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must return a concrete type")
                .hasMessageContaining("java.lang.CharSequence");
    }

    @Test
    void annotatedMethodsCanReadAndWriteKeyedBlackboardValues() {
        KeyedAdapter adapter = new KeyedAdapter();
        List<Action> actions = AnnotatedActionFactory.actions(adapter);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(BlackboardKey.of(OrderId.class, "primary"), new OrderId("O100"));
        blackboard.put(BlackboardKey.of(OrderId.class, "secondary"), new OrderId("O200"));

        actions.getFirst().execute(new com.actiongraph.runtime.DefaultExecutionContext(
                blackboard,
                new InMemoryTraceRepository(),
                "RUN-1"
        ));

        assertThat(blackboard.get(BlackboardKey.of(OrderRecord.class, "selected")))
                .contains(new OrderRecord(new OrderId("O200"), "PAID", false));
        assertThat(blackboard.getAll(OrderId.class))
                .containsExactly(new OrderId("O100"), new OrderId("O200"));
    }

    @Test
    void chineseAnnotationValuesCanBePlannedAndExecuted() {
        ChineseAnnotatedOrderCancellationAdapter adapter = new ChineseAnnotatedOrderCancellationAdapter();
        List<Action> actions = AnnotatedActionFactory.actions(adapter);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new OrderId("O100"));
        blackboard.addCondition(Condition.of("订单取消:已有订单编号"));

        var result = new GoapExecutor(
                new GoapPlanner(),
                new DefaultPolicyGuard(),
                new AutoApproveHumanReviewPolicy(),
                new InMemoryTraceRepository()
        ).run(new Goal("申请订单取消审批", Set.of(Condition.of("订单取消:已发起运营审批"))),
                blackboard,
                actions,
                registry(actions));

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.executedActions()).extracting(actionId -> actionId.value())
                .containsExactly("查询订单", "校验取消资格", "创建取消申请草稿", "发起运营审批");
        assertThat(blackboard.conditions())
                .contains(Condition.of("订单取消:已发起运营审批"));
        assertThat(blackboard.get(OperationsApprovalRequest.class))
                .contains(new OperationsApprovalRequest("OPS-APPROVAL-1", "CANCEL-1"));
    }

    private InMemoryBlackboard blackboard() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new OrderId("O100"));
        blackboard.addCondition(ORDER_ID_PRESENT);
        return blackboard;
    }

    private DefaultActionRegistry registry(List<Action> actions) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        actions.forEach(registry::register);
        return registry;
    }

    private static final class AnnotatedOrderCancellationAdapter {
        private final boolean shipped;
        private final List<String> drafts = new ArrayList<>();
        private final List<String> voidedDrafts = new ArrayList<>();

        private AnnotatedOrderCancellationAdapter(boolean shipped) {
            this.shipped = shipped;
        }

        @ActionGraphAction(
                id = "order.lookup.annotated",
                preconditions = "order-cancellation:ORDER_ID_PRESENT",
                effects = "order-cancellation:ORDER_LOADED",
                riskLevel = ActionRiskLevel.READ_ONLY
        )
        OrderRecord lookup(OrderId orderId) {
            return new OrderRecord(orderId, shipped ? "SHIPPED" : "PAID", shipped);
        }

        @ActionGraphAction(
                id = "order.cancellation.eligibility.check.annotated",
                preconditions = "order-cancellation:ORDER_LOADED",
                effects = "order-cancellation:CANCELLATION_ELIGIBILITY_CHECKED"
        )
        CancellationEligibility check(OrderRecord order) {
            return order.shipped()
                    ? new CancellationEligibility(false, "Already shipped")
                    : new CancellationEligibility(true, "Not shipped");
        }

        @ActionGraphAction(
                id = "order.cancellation.request.draft.annotated",
                preconditions = {
                        "order-cancellation:ORDER_LOADED",
                        "order-cancellation:CANCELLATION_ELIGIBILITY_CHECKED"
                },
                effects = "order-cancellation:CANCELLATION_REQUEST_DRAFTED",
                riskLevel = ActionRiskLevel.MEDIUM
        )
        CancellationRequestDraft draft(OrderRecord order, CancellationEligibility eligibility) {
            CancellationRequestDraft draft = new CancellationRequestDraft("CANCEL-1", order.orderId());
            drafts.add(draft.requestId());
            return draft;
        }

        @ActionGraphGuard(actionId = "order.cancellation.request.draft.annotated")
        boolean canDraft(CancellationEligibility eligibility) {
            return eligibility.eligible();
        }

        @ActionGraphCompensation(actionId = "order.cancellation.request.draft.annotated")
        void voidDraft(CancellationRequestDraft draft) {
            voidedDrafts.add(draft.requestId());
        }

        @ActionGraphAction(
                id = "operations.approval.request.annotated",
                preconditions = "order-cancellation:CANCELLATION_REQUEST_DRAFTED",
                effects = "order-cancellation:OPERATIONS_APPROVAL_REQUESTED",
                riskLevel = ActionRiskLevel.HIGH,
                requiresHumanReview = true
        )
        OperationsApprovalRequest requestApproval(CancellationRequestDraft draft) {
            return new OperationsApprovalRequest("OPS-APPROVAL-1", draft.requestId());
        }

        List<String> drafts() {
            return List.copyOf(drafts);
        }

        List<String> voidedDrafts() {
            return List.copyOf(voidedDrafts);
        }
    }

    private static final class DuplicateActionAdapter {
        @ActionGraphAction(id = "duplicate.id")
        void one() {
        }

        @ActionGraphAction(id = "duplicate.id")
        void two() {
        }
    }

    private static final class InterfaceReturnAdapter {
        @ActionGraphAction(id = "interface.return")
        CharSequence returnsInterface() {
            return "not concrete";
        }
    }

    private static final class KeyedAdapter {
        @ActionGraphAction(id = "keyed.lookup")
        @BlackboardValue("selected")
        OrderRecord lookup(@BlackboardValue("secondary") OrderId orderId) {
            return new OrderRecord(orderId, "PAID", false);
        }
    }

    private static final class ChineseAnnotatedOrderCancellationAdapter {
        @ActionGraphAction(
                id = "查询订单",
                preconditions = "订单取消:已有订单编号",
                effects = "订单取消:已加载订单",
                riskLevel = ActionRiskLevel.READ_ONLY
        )
        OrderRecord 查询订单(OrderId orderId) {
            return new OrderRecord(orderId, "PAID", false);
        }

        @ActionGraphAction(
                id = "校验取消资格",
                preconditions = "订单取消:已加载订单",
                effects = "订单取消:已通过取消资格校验"
        )
        CancellationEligibility 校验取消资格(OrderRecord order) {
            return new CancellationEligibility(!order.shipped(), "订单未发货");
        }

        @ActionGraphAction(
                id = "创建取消申请草稿",
                preconditions = {
                        "订单取消:已加载订单",
                        "订单取消:已通过取消资格校验"
                },
                effects = "订单取消:已创建取消申请草稿",
                riskLevel = ActionRiskLevel.MEDIUM
        )
        CancellationRequestDraft 创建取消申请草稿(OrderRecord order, CancellationEligibility eligibility) {
            return new CancellationRequestDraft("CANCEL-1", order.orderId());
        }

        @ActionGraphGuard(actionId = "创建取消申请草稿")
        boolean 允许创建草稿(CancellationEligibility eligibility) {
            return eligibility.eligible();
        }

        @ActionGraphCompensation(actionId = "创建取消申请草稿")
        void 撤销取消草稿(CancellationRequestDraft draft) {
        }

        @ActionGraphAction(
                id = "发起运营审批",
                preconditions = "订单取消:已创建取消申请草稿",
                effects = "订单取消:已发起运营审批",
                riskLevel = ActionRiskLevel.HIGH,
                requiresHumanReview = true
        )
        OperationsApprovalRequest 发起运营审批(CancellationRequestDraft draft) {
            return new OperationsApprovalRequest("OPS-APPROVAL-1", draft.requestId());
        }
    }

    private record OrderId(String value) {
        OrderId {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException("OrderId value must not be blank");
            }
        }
    }

    private record OrderRecord(OrderId orderId, String status, boolean shipped) {
    }

    private record CancellationEligibility(boolean eligible, String reason) {
    }

    private record CancellationRequestDraft(String requestId, OrderId orderId) {
    }

    private record OperationsApprovalRequest(String approvalId, String requestId) {
    }
}
