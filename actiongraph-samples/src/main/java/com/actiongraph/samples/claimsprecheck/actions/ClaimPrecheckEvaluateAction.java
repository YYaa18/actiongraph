package com.actiongraph.samples.claimsprecheck.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.domain.ClaimDocumentBundle;
import com.actiongraph.samples.claimsprecheck.domain.ClaimPrecheckResult;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;
import com.actiongraph.samples.claimsprecheck.service.ClaimPrecheckService;

import java.util.Set;

public final class ClaimPrecheckEvaluateAction implements Action {
    public static final ActionId ID = new ActionId("claim.precheck.evaluate");

    private final ClaimPrecheckService precheckService;

    public ClaimPrecheckEvaluateAction(ClaimPrecheckService precheckService) {
        this.precheckService = precheckService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(ClaimRecord.class, ClaimDocumentBundle.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(ClaimPrecheckResult.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(ClaimsPrecheckConditions.CLAIM_LOADED, ClaimsPrecheckConditions.DOCUMENTS_LOADED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(ClaimsPrecheckConditions.PRECHECK_COMPLETED);
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
        ClaimRecord claim = context.blackboard().get(ClaimRecord.class)
                .orElseThrow(() -> new IllegalStateException("ClaimRecord missing"));
        ClaimDocumentBundle documents = context.blackboard().get(ClaimDocumentBundle.class)
                .orElseThrow(() -> new IllegalStateException("ClaimDocumentBundle missing"));
        context.blackboard().put(precheckService.evaluate(claim, documents));
        return ActionResult.ok();
    }
}
