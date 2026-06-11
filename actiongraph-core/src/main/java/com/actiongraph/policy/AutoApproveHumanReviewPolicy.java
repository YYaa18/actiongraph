package com.actiongraph.policy;

/**
 * Human-review policy that approves every request.
 *
 * <p>Use this only for tests, demos, or carefully controlled automation.
 * Production systems should normally persist review tasks and resume runs after
 * an explicit external decision.
 */
public final class AutoApproveHumanReviewPolicy implements HumanReviewPolicy {
    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return HumanReviewResult.approved("auto-approve", "Approved by configured auto-approval policy");
    }
}
