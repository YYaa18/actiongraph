package com.actiongraph.runtime;

/**
 * Runtime outcome visible to callers and control-plane APIs.
 *
 * <p>Status values are part of the public contract. Adding, renaming, or
 * changing their terminal/suspended meaning is a compatibility-sensitive
 * change.
 */
public enum RunStatus {
    /** The goal was satisfied. */
    COMPLETED,
    /** No available plan can reach the goal from the current state. */
    HALTED_UNREACHABLE,
    /** The run is paused and waiting for an external human-review decision. */
    SUSPENDED_PENDING_REVIEW,
    /** Policy or human review denied the run and compensation completed. */
    DENIED_BY_POLICY,
    /** Execution failed and all required compensation completed. */
    FAILED_COMPENSATED,
    /** Execution or denial failed and at least one compensation failed. */
    FAILED_COMPENSATION_INCOMPLETE
}
