package com.actiongraph.samples.renewal.actions;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.domain.CurrentContract;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.service.ContractService;
import com.actiongraph.planning.Condition;

import java.util.Set;

public final class CurrentContractQueryAction implements Action {
    public static final ActionId ID = new ActionId("contract.current.query");

    private final ContractService contractService;

    public CurrentContractQueryAction(ContractService contractService) {
        this.contractService = contractService;
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
        return Set.of(CurrentContract.class);
    }

    @Override
    public Set<Condition> preconditions() {
        return Set.of(RenewalConditions.CUSTOMER_ID_PRESENT);
    }

    @Override
    public Set<Condition> effects() {
        return Set.of(RenewalConditions.CURRENT_CONTRACT_LOADED);
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
    public boolean runtimeGuard(com.actiongraph.runtime.Blackboard blackboard) {
        CustomerId customerId = blackboard.get(CustomerId.class)
                .orElseThrow(() -> new IllegalStateException("CustomerId missing"));
        return contractService.hasCurrent(customerId);
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        CustomerId customerId = context.blackboard().get(CustomerId.class)
                .orElseThrow(() -> new IllegalStateException("CustomerId missing"));
        context.blackboard().put(contractService.findCurrent(customerId));
        return ActionResult.ok();
    }
}
