package com.actiongraph.governance.humanreview;

import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.policy.ApprovalChain;
import com.actiongraph.policy.ApprovalChainResolver;
import com.actiongraph.policy.ApprovalStage;
import com.actiongraph.policy.HumanReviewRequest;

import java.util.ArrayList;
import java.util.List;

public final class RiskBasedChainResolver implements ApprovalChainResolver {
    private static final ApprovalStage CHECKER = new ApprovalStage("checker-review", "checker");
    private static final ApprovalStage AUTHORIZER = new ApprovalStage("authorization", "authorizer");
    private static final ApprovalStage AMOUNT_AUTHORIZER = new ApprovalStage("amount-authorization", "authorizer");

    @Override
    public ApprovalChain resolve(HumanReviewRequest request) {
        List<ApprovalStage> stages = new ArrayList<>();
        if (request.riskLevel() == ActionRiskLevel.HIGH) {
            stages.add(CHECKER);
            stages.add(AUTHORIZER);
        } else {
            stages.addAll(ApprovalChain.single().stages());
        }
        if ("true".equalsIgnoreCase(request.attributes().get("amountEscalated"))) {
            stages.add(AMOUNT_AUTHORIZER);
        }
        return new ApprovalChain(stages);
    }
}
