package com.actiongraph.policy;

/**
 * Human-review policy that always suspends for later decision.
 *
 * <p>This is the safe default for production-like wiring because it avoids
 * silent auto-approval. A control plane or external approval callback should
 * later claim and resume the suspended run.
 */
public final class PendingHumanReviewPolicy implements HumanReviewPolicy {
    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return HumanReviewResult.pending("Human review is required before executing " + request.actionId().value());
    }
}
