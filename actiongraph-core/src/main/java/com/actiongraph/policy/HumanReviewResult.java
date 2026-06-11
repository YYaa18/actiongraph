package com.actiongraph.policy;

/**
 * Decision returned by a {@link HumanReviewPolicy}.
 *
 * <p>{@link HumanReviewDecision#PENDING} suspends the run without compensation.
 * {@link HumanReviewDecision#DENIED} is a terminal outcome and the runtime
 * compensates any previously successful actions. {@link HumanReviewDecision#APPROVED}
 * allows the pending action to execute.
 *
 * @param decision non-null review decision
 * @param reviewer reviewer identifier for audit; {@code null} becomes empty
 * @param message review detail for audit; {@code null} becomes empty
 */
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

    /**
     * Creates an approved result.
     *
     * @param reviewer reviewer identifier
     * @param message review detail
     * @return approved result
     */
    public static HumanReviewResult approved(String reviewer, String message) {
        return new HumanReviewResult(HumanReviewDecision.APPROVED, reviewer, message);
    }

    /**
     * Creates a denied result.
     *
     * @param reviewer reviewer identifier
     * @param message review detail
     * @return denied result
     */
    public static HumanReviewResult denied(String reviewer, String message) {
        return new HumanReviewResult(HumanReviewDecision.DENIED, reviewer, message);
    }

    /**
     * Creates a pending result that suspends the run for later resume.
     *
     * @param message review detail
     * @return pending result
     */
    public static HumanReviewResult pending(String message) {
        return new HumanReviewResult(HumanReviewDecision.PENDING, "", message);
    }
}
