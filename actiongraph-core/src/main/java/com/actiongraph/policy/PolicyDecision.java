package com.actiongraph.policy;

/**
 * Three-valued runtime policy outcome.
 *
 * <p>The enum is intentionally closed and stable: {@link #ALLOW} executes,
 * {@link #REQUIRES_HUMAN_REVIEW} routes through human review, and {@link #DENY}
 * terminates the run with compensation for prior side effects.
 */
public enum PolicyDecision {
    ALLOW,
    REQUIRES_HUMAN_REVIEW,
    DENY
}
