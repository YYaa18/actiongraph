package com.actiongraph.samples.renewal.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.domain.QuoteDraft;
import com.actiongraph.samples.renewal.service.ApprovalService;
import com.actiongraph.planning.Condition;

import java.util.Set;

public final class SalesApprovalRequestAction implements Action {
    public static final ActionId ID = new ActionId("sales.approval.request");

    private final ApprovalService approvalService;

    public SalesApprovalRequestAction(ApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(QuoteDraft.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(ApprovalRequest.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(RenewalConditions.QUOTE_DRAFT_CREATED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(RenewalConditions.SALES_APPROVAL_REQUESTED);
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
        QuoteDraft draft = context.blackboard().get(QuoteDraft.class)
                .orElseThrow(() -> new IllegalStateException("QuoteDraft missing"));
        context.blackboard().put(approvalService.request(draft));
        return ActionResult.ok();
    }

    @Override
    public CompensationResult compensate(ExecutionContext context) {
        return context.blackboard().get(ApprovalRequest.class)
                .map(request -> {
                    approvalService.withdraw(request.approvalId());
                    return CompensationResult.ok("Withdrew approval request " + request.approvalId());
                })
                .orElseGet(CompensationResult::noop);
    }
}
