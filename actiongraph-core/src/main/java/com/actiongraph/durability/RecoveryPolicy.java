package com.actiongraph.durability;

import com.actiongraph.api.Experimental;

/**
 * Strategy used when recovering a stale running checkpoint.
 */
@Experimental(
        since = "0.2.0",
        value = "Recovery policy is experimental until MS1 crash-recovery pilots complete."
)
public enum RecoveryPolicy {
    /**
     * Compensate any unknown in-flight action, then replan from the checkpoint
     * and continue toward the goal.
     */
    CONTINUE,
    /**
     * Compensate any unknown in-flight action and then compensate the whole
     * existing stack, ending the run conservatively.
     */
    COMPENSATE
}
