package com.actiongraph.policy;

public final class DenyingHumanReviewPolicy implements HumanReviewPolicy {
    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return HumanReviewResult.denied("policy", "Human review denied " + request.actionId().value());
    }
}
