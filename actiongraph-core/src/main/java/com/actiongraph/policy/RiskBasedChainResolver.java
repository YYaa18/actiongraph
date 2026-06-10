package com.actiongraph.policy;

import com.actiongraph.action.ActionRiskLevel;

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
