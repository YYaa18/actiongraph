package com.actiongraph.policy;

public final class AutoApproveHumanReviewPolicy implements HumanReviewPolicy {
    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return HumanReviewResult.approved("auto-approve", "Approved by configured auto-approval policy");
    }
}
