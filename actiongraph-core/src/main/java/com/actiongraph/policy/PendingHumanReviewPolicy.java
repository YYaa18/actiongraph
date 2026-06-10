package com.actiongraph.policy;

public final class PendingHumanReviewPolicy implements HumanReviewPolicy {
    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return HumanReviewResult.pending("Human review is required before executing " + request.actionId().value());
    }
}
