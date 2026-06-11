package com.actiongraph.policy;

/**
 * Decision returned by a human-review integration.
 *
 * <p>{@link #PENDING} is non-terminal and preserves the suspended snapshot for a
 * later resume. {@link #DENIED} is terminal and triggers compensation for work
 * already completed in the run.
 */
public enum HumanReviewDecision {
    APPROVED,
    DENIED,
    PENDING
}
