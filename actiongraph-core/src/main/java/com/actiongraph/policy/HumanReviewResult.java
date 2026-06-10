package com.actiongraph.policy;

public record HumanReviewResult(
        HumanReviewDecision decision,
        String reviewer,
        String message
) {
    public HumanReviewResult {
        if (decision == null) {
            throw new IllegalArgumentException("decision must not be null");
        }
        reviewer = reviewer == null ? "" : reviewer;
        message = message == null ? "" : message;
    }

    public static HumanReviewResult approved(String reviewer, String message) {
        return new HumanReviewResult(HumanReviewDecision.APPROVED, reviewer, message);
    }

    public static HumanReviewResult denied(String reviewer, String message) {
        return new HumanReviewResult(HumanReviewDecision.DENIED, reviewer, message);
    }

    public static HumanReviewResult pending(String message) {
        return new HumanReviewResult(HumanReviewDecision.PENDING, "", message);
    }
}
