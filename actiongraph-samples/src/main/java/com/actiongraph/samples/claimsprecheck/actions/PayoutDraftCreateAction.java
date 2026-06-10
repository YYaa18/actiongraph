package com.actiongraph.samples.claimsprecheck.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.domain.ClaimPrecheckResult;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;
import com.actiongraph.samples.claimsprecheck.service.PayoutDraftService;

import java.util.Set;

public final class PayoutDraftCreateAction implements Action {
    public static final ActionId ID = new ActionId("claim.payout.draft.create");

    private final PayoutDraftService draftService;

    public PayoutDraftCreateAction(PayoutDraftService draftService) {
        this.draftService = draftService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(ClaimRecord.class, ClaimPrecheckResult.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(PayoutApplicationDraft.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(ClaimsPrecheckConditions.CLAIM_LOADED, ClaimsPrecheckConditions.PRECHECK_COMPLETED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(ClaimsPrecheckConditions.PAYOUT_DRAFT_CREATED);
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
        return blackboard.get(ClaimPrecheckResult.class)
                .map(ClaimPrecheckResult::complete)
                .orElse(false);
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        ClaimRecord claim = context.blackboard().get(ClaimRecord.class)
                .orElseThrow(() -> new IllegalStateException("ClaimRecord missing"));
        ClaimPrecheckResult precheck = context.blackboard().get(ClaimPrecheckResult.class)
                .orElseThrow(() -> new IllegalStateException("ClaimPrecheckResult missing"));
        context.blackboard().put(draftService.createDraft(claim, precheck));
        return ActionResult.ok();
    }

    @Override
    public CompensationResult compensate(ExecutionContext context) {
        return context.blackboard().get(PayoutApplicationDraft.class)
                .map(draft -> {
                    draftService.voidDraft(draft.draftId());
                    return CompensationResult.ok("Voided payout draft " + draft.draftId());
                })
                .orElseGet(CompensationResult::noop);
    }
}
