package com.actiongraph.runtime;

import com.actiongraph.api.Experimental;

/**
 * Durable run snapshot kind stored by {@link SuspendedRunRepository}.
 *
 * <p>Human-review suspension and crash-recovery checkpoints share the same
 * snapshot payload. This state distinguishes why the snapshot is currently
 * visible without adding public {@link RunStatus} values.
 */
@Experimental(
        since = "0.2.0",
        value = "Durable run checkpoint state is experimental until MS1 crash-recovery pilots complete."
)
public enum SnapshotState {
    /** The run is active; this row is a crash-recovery checkpoint. */
    RUNNING,
    /** The run is paused for human review and must be resumed explicitly. */
    SUSPENDED,
    /** The run is paused until a correlated external event is delivered. */
    WAITING_EVENT
}
