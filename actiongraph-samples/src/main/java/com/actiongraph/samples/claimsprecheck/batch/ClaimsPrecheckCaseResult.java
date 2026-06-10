package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.runtime.RunStatus;

public record ClaimsPrecheckCaseResult(
        String claimId,
        RunStatus status,
        boolean businessIntercepted,
        boolean auditComplete,
        long elapsedNanos,
        int executedActionCount,
        int traceEventCount
) {
    public double elapsedMillis() {
        return elapsedNanos / 1_000_000.0;
    }
}
