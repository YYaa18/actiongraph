package com.actiongraph.observability;

/**
 * Stable lifecycle event taxonomy emitted by the runtime observability SPI.
 *
 * <p>These events are intentionally coarser than trace events. They are meant
 * for metrics, counters, timers, and health dashboards rather than audit.
 */
public enum ObservationEventType {
    RUN_STARTED,
    RUN_RESUMED,
    RUN_FINISHED,
    PLAN_GENERATED,
    NO_PLAN,
    POLICY_EVALUATED,
    HUMAN_REVIEW_REQUESTED,
    HUMAN_REVIEW_DECIDED,
    RUNTIME_GUARD_FAILED,
    ACTION_STARTED,
    ACTION_FINISHED,
    COMPENSATION_STARTED,
    COMPENSATION_FINISHED,
    TRACE_FLUSHED
}
