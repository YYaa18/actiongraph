package com.actiongraph.policy;

/**
 * Human-review policy that denies every request.
 *
 * <p>Useful for tests that validate terminal denial and compensation behavior.
 */
public final class DenyingHumanReviewPolicy implements HumanReviewPolicy {
    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return HumanReviewResult.denied("policy", "Human review denied " + request.actionId().value());
    }
}
