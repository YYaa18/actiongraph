package com.actiongraph.runtime;

import java.util.Objects;

public final class SuspendedRunNotClaimableException extends IllegalStateException {
    private final String runId;

    public SuspendedRunNotClaimableException(String runId) {
        super("No resumable suspended run can be claimed for runId: " + runId
                + " (it may already be resuming, already completed, or missing)");
        this.runId = Objects.requireNonNull(runId, "runId");
    }

    public String runId() {
        return runId;
    }
}
