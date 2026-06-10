package com.actiongraph.samples.claimsprecheck.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.domain.ClaimId;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;
import com.actiongraph.samples.claimsprecheck.service.ClaimService;

import java.util.Set;

public final class ClaimLookupAction implements Action {
    public static final ActionId ID = new ActionId("claim.lookup");

    private final ClaimService claimService;

    public ClaimLookupAction(ClaimService claimService) {
        this.claimService = claimService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(ClaimId.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(ClaimRecord.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(ClaimsPrecheckConditions.CLAIM_ID_PRESENT);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(ClaimsPrecheckConditions.CLAIM_LOADED);
    }

    @Override
    public int cost() {
        return 1;
    }

    @Override
    public ActionRiskLevel riskLevel() {
        return ActionRiskLevel.READ_ONLY;
    }

    @Override
    public boolean requiresHumanReview() {
        return false;
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        ClaimId claimId = context.blackboard().get(ClaimId.class)
                .orElseThrow(() -> new IllegalStateException("ClaimId missing"));
        context.blackboard().put(claimService.findClaim(claimId));
        return ActionResult.ok();
    }
}
