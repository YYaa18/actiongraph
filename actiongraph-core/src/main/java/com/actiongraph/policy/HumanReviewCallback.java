package com.actiongraph.policy;

import com.actiongraph.action.ActionId;

import java.util.Objects;

public record HumanReviewCallback(
        String runId,
        ActionId actionId,
        int expectedStageIndex,
        HumanReviewDecision decision,
        String reviewer,
        String comment
) {
    public HumanReviewCallback {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(actionId, "actionId");
        if (expectedStageIndex < 0) {
            throw new IllegalArgumentException("expectedStageIndex must not be negative");
        }
        Objects.requireNonNull(decision, "decision");
        if (decision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("human review callback decision must be APPROVED or DENIED");
        }
        reviewer = reviewer == null || reviewer.isBlank() ? "external-reviewer" : reviewer;
        comment = comment == null ? "" : comment;
    }
}
