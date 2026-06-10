package com.actiongraph.samples.ordercancellation.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.ordercancellation.OrderCancellationConditions;
import com.actiongraph.samples.ordercancellation.domain.CancellationEligibility;
import com.actiongraph.samples.ordercancellation.domain.CancellationRequestDraft;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;
import com.actiongraph.samples.ordercancellation.service.CancellationRequestService;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;

import java.util.Set;

public final class CancellationRequestDraftAction implements Action {
    public static final ActionId ID = new ActionId("order.cancellation.request.draft");

    private final CancellationRequestService requestService;

    public CancellationRequestDraftAction(CancellationRequestService requestService) {
        this.requestService = requestService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(OrderRecord.class, CancellationEligibility.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(CancellationRequestDraft.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(
                OrderCancellationConditions.ORDER_LOADED,
                OrderCancellationConditions.CANCELLATION_ELIGIBILITY_CHECKED
        );
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(OrderCancellationConditions.CANCELLATION_REQUEST_DRAFTED);
    }

    @Override
    public int cost() {
        return 1;
    }

    @Override
    public ActionRiskLevel riskLevel() {
        return ActionRiskLevel.MEDIUM;
    }

    @Override
    public boolean requiresHumanReview() {
        return false;
    }

    @Override
    public boolean runtimeGuard(Blackboard blackboard) {
        return blackboard.get(CancellationEligibility.class)
                .map(CancellationEligibility::eligible)
                .orElse(false);
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        OrderRecord order = context.blackboard().get(OrderRecord.class)
                .orElseThrow(() -> new IllegalStateException("OrderRecord missing"));
        CancellationEligibility eligibility = context.blackboard().get(CancellationEligibility.class)
                .orElseThrow(() -> new IllegalStateException("CancellationEligibility missing"));
        context.blackboard().put(requestService.createDraft(order, eligibility));
        return ActionResult.ok();
    }

    @Override
    public CompensationResult compensate(ExecutionContext context) {
        return context.blackboard().get(CancellationRequestDraft.class)
                .map(draft -> {
                    requestService.voidDraft(draft.requestId());
                    return CompensationResult.ok("Voided cancellation request " + draft.requestId());
                })
                .orElseGet(CompensationResult::noop);
    }
}
