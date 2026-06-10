package com.actiongraph.samples.ordercancellation.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.ordercancellation.OrderCancellationConditions;
import com.actiongraph.samples.ordercancellation.domain.CancellationRequestDraft;
import com.actiongraph.samples.ordercancellation.domain.OperationsApprovalRequest;
import com.actiongraph.samples.ordercancellation.service.OperationsApprovalService;
import com.actiongraph.planning.Condition;

import java.util.Set;

public final class OperationsApprovalRequestAction implements Action {
    public static final ActionId ID = new ActionId("operations.approval.request");

    private final OperationsApprovalService approvalService;

    public OperationsApprovalRequestAction(OperationsApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(CancellationRequestDraft.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(OperationsApprovalRequest.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(OrderCancellationConditions.CANCELLATION_REQUEST_DRAFTED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(OrderCancellationConditions.OPERATIONS_APPROVAL_REQUESTED);
    }

    @Override
    public int cost() {
        return 1;
    }

    @Override
    public ActionRiskLevel riskLevel() {
        return ActionRiskLevel.HIGH;
    }

    @Override
    public boolean requiresHumanReview() {
        return true;
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        CancellationRequestDraft draft = context.blackboard().get(CancellationRequestDraft.class)
                .orElseThrow(() -> new IllegalStateException("CancellationRequestDraft missing"));
        context.blackboard().put(approvalService.request(draft));
        return ActionResult.ok();
    }

    @Override
    public CompensationResult compensate(ExecutionContext context) {
        return context.blackboard().get(OperationsApprovalRequest.class)
                .map(request -> {
                    approvalService.withdraw(request.approvalId());
                    return CompensationResult.ok("Withdrew operations approval " + request.approvalId());
                })
                .orElseGet(CompensationResult::noop);
    }
}
