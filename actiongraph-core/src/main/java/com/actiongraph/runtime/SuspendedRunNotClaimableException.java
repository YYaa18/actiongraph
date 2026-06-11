package com.actiongraph.runtime;

import java.util.Objects;

/**
 * Raised when a resume request cannot atomically claim a suspended snapshot.
 *
 * <p>Callers should normally treat this as an idempotent outcome for duplicate
 * approval callbacks or retry races, not as evidence that the business action
 * failed. The run id may be missing, already completed, or currently claimed by
 * another worker.
 */
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
