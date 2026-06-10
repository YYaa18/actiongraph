package com.actiongraph.samples.claimsprecheck.batch;

public record ClaimsPrecheckBatchReviewOptions(
        boolean suspendResume,
        long simulatedReviewWaitMillis
) {
    public ClaimsPrecheckBatchReviewOptions {
        if (simulatedReviewWaitMillis < 0) {
            throw new IllegalArgumentException("simulatedReviewWaitMillis must not be negative");
        }
    }

    public static ClaimsPrecheckBatchReviewOptions autoApprove() {
        return new ClaimsPrecheckBatchReviewOptions(false, 0);
    }

    public static ClaimsPrecheckBatchReviewOptions suspendResume(long simulatedReviewWaitMillis) {
        return new ClaimsPrecheckBatchReviewOptions(true, simulatedReviewWaitMillis);
    }

    public String modeName() {
        return suspendResume ? "suspend-resume" : "auto-approve";
    }
}
