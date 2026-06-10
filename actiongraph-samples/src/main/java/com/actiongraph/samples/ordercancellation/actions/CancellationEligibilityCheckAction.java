package com.actiongraph.samples.ordercancellation.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.ordercancellation.OrderCancellationConditions;
import com.actiongraph.samples.ordercancellation.domain.CancellationEligibility;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;
import com.actiongraph.samples.ordercancellation.service.CancellationPolicyService;
import com.actiongraph.planning.Condition;

import java.util.Set;

public final class CancellationEligibilityCheckAction implements Action {
    public static final ActionId ID = new ActionId("order.cancellation.eligibility.check");

    private final CancellationPolicyService policyService;

    public CancellationEligibilityCheckAction(CancellationPolicyService policyService) {
        this.policyService = policyService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(OrderRecord.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(CancellationEligibility.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(OrderCancellationConditions.ORDER_LOADED);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(OrderCancellationConditions.CANCELLATION_ELIGIBILITY_CHECKED);
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
        OrderRecord order = context.blackboard().get(OrderRecord.class)
                .orElseThrow(() -> new IllegalStateException("OrderRecord missing"));
        context.blackboard().put(policyService.check(order));
        return ActionResult.ok();
    }
}
