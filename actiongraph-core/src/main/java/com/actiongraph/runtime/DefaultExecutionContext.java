package com.actiongraph.runtime;

import com.actiongraph.api.Internal;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.trace.TraceRepository;

/**
 * Immutable default {@link ExecutionContext} implementation.
 *
 * <p>The record only holds references; the Blackboard and trace repository may
 * themselves be mutable. Do not retain a context beyond the action invocation
 * that received it.
 */
@Internal
public record DefaultExecutionContext(
        Blackboard blackboard,
        TraceRepository trace,
        String runId,
        int attempt
) implements ExecutionContext {
    public DefaultExecutionContext(Blackboard blackboard, TraceRepository trace, String runId) {
        this(blackboard, trace, runId, 1);
    }
}
