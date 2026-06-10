package com.actiongraph.samples.renewal.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.domain.CustomerProfile;
import com.actiongraph.samples.renewal.service.CustomerService;
import com.actiongraph.planning.Condition;

import java.util.Set;

public final class CustomerProfileQueryAction implements Action {
    public static final ActionId ID = new ActionId("customer.profile.query");

    private final CustomerService customerService;

    public CustomerProfileQueryAction(CustomerService customerService) {
        this.customerService = customerService;
    }

    @Override
    public ActionId id() {
        return ID;
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return Set.of(CustomerId.class);
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return Set.of(CustomerProfile.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(RenewalConditions.CUSTOMER_ID_PRESENT);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(RenewalConditions.CUSTOMER_PROFILE_LOADED);
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
        CustomerId customerId = context.blackboard().get(CustomerId.class)
                .orElseThrow(() -> new IllegalStateException("CustomerId missing"));
        context.blackboard().put(customerService.findProfile(customerId));
        return ActionResult.ok();
    }
}
