package com.actiongraph.trace;

import com.actiongraph.api.Experimental;

/**
 * Stable taxonomy of runtime audit events.
 *
 * <p>Event names are consumed by tests, control planes, dashboards, and
 * persisted audit queries. Additions are compatibility-sensitive and should be
 * documented in the changelog.
 */
public enum TraceEventType {
    RUN_STARTED,
    RUN_RESUMED,
    RUN_SUSPENDED,
    PLAN_GENERATED,
    NO_PLAN,
    POLICY_EVALUATED,
    POLICY_DENIED,
    HUMAN_REVIEW_REQUESTED,
    HUMAN_REVIEW_DECIDED,
    RUNTIME_GUARD_FAILED,
    ACTION_STARTED,
    @Experimental(
            since = "0.1.0",
            value = "Retry trace events are experimental until retry/idempotency conventions settle."
    )
    ACTION_RETRIED,
    @Experimental(
            since = "0.1.0",
            value = "Timeout trace events are experimental until unknown-outcome compensation conventions settle."
    )
    ACTION_TIMED_OUT,
    ACTION_SUCCEEDED,
    ACTION_FAILED,
    BLACKBOARD_UPDATED,
    COMPENSATED,
    COMPENSATION_ERROR,
    GOAL_SATISFIED,
    RUN_ENDED
}
