package com.actiongraph.samples.renewal;

import com.actiongraph.action.Action;
import com.actiongraph.action.DefaultActionRegistry;
import com.actiongraph.samples.renewal.actions.CurrentContractQueryAction;
import com.actiongraph.samples.renewal.actions.CustomerProfileQueryAction;
import com.actiongraph.samples.renewal.actions.QuoteDraftCreateAction;
import com.actiongraph.samples.renewal.actions.RenewalEligibilityCheckAction;
import com.actiongraph.samples.renewal.actions.SalesApprovalRequestAction;
import com.actiongraph.samples.renewal.actions.SyntheticCurrentContractCreateAction;
import com.actiongraph.samples.renewal.service.ApprovalService;
import com.actiongraph.samples.renewal.service.ContractService;
import com.actiongraph.samples.renewal.service.CustomerService;
import com.actiongraph.samples.renewal.service.QuoteService;
import com.actiongraph.samples.renewal.service.RenewalPolicyService;

import java.util.List;

public final class RenewalActionFactory {
    private RenewalActionFactory() {
    }

    public static List<Action> actions(
            CustomerService customerService,
            ContractService contractService,
            RenewalPolicyService renewalPolicyService,
            QuoteService quoteService,
            ApprovalService approvalService
    ) {
        return List.of(
                new CustomerProfileQueryAction(customerService),
                new CurrentContractQueryAction(contractService),
                new SyntheticCurrentContractCreateAction(),
                new RenewalEligibilityCheckAction(renewalPolicyService),
                new QuoteDraftCreateAction(quoteService),
                new SalesApprovalRequestAction(approvalService)
        );
    }

    public static DefaultActionRegistry registry(List<Action> actions) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        actions.forEach(registry::register);
        return registry;
    }
}
