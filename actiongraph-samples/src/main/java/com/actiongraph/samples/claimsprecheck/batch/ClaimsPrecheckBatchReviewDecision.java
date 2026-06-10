package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.HumanReviewDecision;

import java.util.Objects;

public record ClaimsPrecheckBatchReviewDecision(
        String claimId,
        ActionId actionId,
        int stageIndex,
        HumanReviewDecision decision,
        String reviewer,
        String comment,
        long decisionDelayMillis
) {
    public ClaimsPrecheckBatchReviewDecision {
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("claimId must not be blank");
        }
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(decision, "decision");
        if (decision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("review decision must be APPROVED or DENIED");
        }
        if (stageIndex < 0) {
            throw new IllegalArgumentException("stageIndex must not be negative");
        }
        if (decisionDelayMillis < 0) {
            throw new IllegalArgumentException("decisionDelayMillis must not be negative");
        }
        reviewer = reviewer == null || reviewer.isBlank() ? "external-reviewer" : reviewer;
        comment = comment == null ? "" : comment;
    }

    public static ClaimsPrecheckBatchReviewDecision approve(
            String claimId,
            ActionId actionId,
            int stageIndex,
            long decisionDelayMillis
    ) {
        return new ClaimsPrecheckBatchReviewDecision(
                claimId,
                actionId,
                stageIndex,
                HumanReviewDecision.APPROVED,
                "batch-simulated-reviewer",
                "Approved by claims precheck batch simulation",
                decisionDelayMillis
        );
    }
}
