package com.actiongraph.samples.renewal;

import com.actiongraph.action.Action;
import com.actiongraph.contribution.ActionGraphContribution;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.samples.renewal.service.ApprovalService;
import com.actiongraph.samples.renewal.service.ContractService;
import com.actiongraph.samples.renewal.service.CustomerService;
import com.actiongraph.samples.renewal.service.QuoteService;
import com.actiongraph.samples.renewal.service.RenewalPolicyService;

import java.util.List;
import java.util.Objects;

public final class RenewalContribution implements ActionGraphContribution {
    private final CustomerService customerService;
    private final ContractService contractService;
    private final RenewalPolicyService renewalPolicyService;
    private final QuoteService quoteService;
    private final ApprovalService approvalService;

    public RenewalContribution(
            CustomerService customerService,
            ContractService contractService,
            RenewalPolicyService renewalPolicyService,
            QuoteService quoteService,
            ApprovalService approvalService
    ) {
        this.customerService = Objects.requireNonNull(customerService, "customerService");
        this.contractService = Objects.requireNonNull(contractService, "contractService");
        this.renewalPolicyService = Objects.requireNonNull(renewalPolicyService, "renewalPolicyService");
        this.quoteService = Objects.requireNonNull(quoteService, "quoteService");
        this.approvalService = Objects.requireNonNull(approvalService, "approvalService");
    }

    @Override
    public List<Action> actions() {
        return RenewalActionFactory.actions(
                customerService,
                contractService,
                renewalPolicyService,
                quoteService,
                approvalService
        );
    }

    @Override
    public List<GoalDefinition> goals() {
        return RenewalGoalAnnotations.goals();
    }

    @Override
    public List<GoalBlackboardSeeder> seeders() {
        return RenewalGoalAnnotations.seeders();
    }
}
