package com.actiongraph.samples.claimsprecheck.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.domain.ClaimApprovalRequest;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;
import com.actiongraph.samples.claimsprecheck.service.ClaimApprovalService;

import java.util.Set;

public final class ClaimApprovalRequestAction implements Action {
    public static final ActionId ID = new ActionId("claim.approval.request");

    private final ClaimApprovalService approvalService;

    public ClaimApprovalRequestAction(ClaimApprovalService approvalService) {
        this.approvalService = approvalService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(PayoutApplicationDraft.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(ClaimApprovalRequest.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(ClaimsPrecheckConditions.PAYOUT_DRAFT_CREATED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(ClaimsPrecheckConditions.CLAIM_APPROVAL_REQUESTED);
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
        PayoutApplicationDraft draft = context.blackboard().get(PayoutApplicationDraft.class)
                .orElseThrow(() -> new IllegalStateException("PayoutApplicationDraft missing"));
        context.blackboard().put(approvalService.requestApproval(draft));
        return ActionResult.ok();
    }

    @Override
    public CompensationResult compensate(ExecutionContext context) {
        return context.blackboard().get(ClaimApprovalRequest.class)
                .map(request -> {
                    approvalService.withdraw(request.requestId());
                    return CompensationResult.ok("Withdrew claim approval request " + request.requestId());
                })
                .orElseGet(CompensationResult::noop);
    }
}
