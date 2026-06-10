package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.runtime.RunStatus;

public record ClaimsPrecheckCaseResult(
        String claimId,
        RunStatus status,
        boolean businessIntercepted,
        boolean auditComplete,
        long elapsedNanos,
        long businessActionNanos,
        long reviewWaitNanos,
        long frameworkNanos,
        int executedActionCount,
        int traceEventCount
) {
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }

    public double businessActionMillis() {
        return businessActionNanos / 1_000_000.0;
    }

    public double reviewWaitMillis() {
        return reviewWaitNanos / 1_000_000.0;
    }

    public double frameworkMillis() {
        return frameworkNanos / 1_000_000.0;
    }
}
