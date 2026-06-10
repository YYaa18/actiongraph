package com.actiongraph.policy;

import java.time.Instant;
import java.util.Objects;

public record StageDecision(
        String stage,
        HumanReviewDecision decision,
        String reviewer,
        String comment,
        Instant at
) {
    public StageDecision {
        if (stage == null || stage.isBlank()) {
            throw new IllegalArgumentException("stage must not be blank");
        }
        Objects.requireNonNull(decision, "decision");
        if (decision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("stage decision must be APPROVED or DENIED");
        }
        reviewer = reviewer == null ? "" : reviewer;
        comment = comment == null ? "" : comment;
        at = Objects.requireNonNull(at, "at");
    }
}
