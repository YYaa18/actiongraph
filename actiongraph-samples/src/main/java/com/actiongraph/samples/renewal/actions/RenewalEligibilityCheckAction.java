package com.actiongraph.samples.renewal.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.domain.CurrentContract;
import com.actiongraph.samples.renewal.domain.RenewalEligibility;
import com.actiongraph.samples.renewal.service.RenewalPolicyService;
import com.actiongraph.planning.Condition;

import java.util.Set;

public final class RenewalEligibilityCheckAction implements Action {
    public static final ActionId ID = new ActionId("renewal.eligibility.check");

    private final RenewalPolicyService renewalPolicyService;

    public RenewalEligibilityCheckAction(RenewalPolicyService renewalPolicyService) {
        this.renewalPolicyService = renewalPolicyService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(CurrentContract.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(RenewalEligibility.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(RenewalConditions.CURRENT_CONTRACT_LOADED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(RenewalConditions.RENEWAL_ELIGIBILITY_CHECKED);
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
        CurrentContract contract = context.blackboard().get(CurrentContract.class)
                .orElseThrow(() -> new IllegalStateException("CurrentContract missing"));
        context.blackboard().put(renewalPolicyService.check(contract));
        return ActionResult.ok();
    }
}
