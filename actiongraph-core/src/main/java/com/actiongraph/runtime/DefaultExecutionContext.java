package com.actiongraph.runtime;

import com.actiongraph.api.Internal;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.trace.TraceRepository;

import java.util.Objects;

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
        int attempt,
        RunPrincipal principal
) implements ExecutionContext {
    public DefaultExecutionContext(Blackboard blackboard, TraceRepository trace, String runId) {
        this(blackboard, trace, runId, 1, RunPrincipal.anonymous());
    }

    public DefaultExecutionContext(Blackboard blackboard, TraceRepository trace, String runId, int attempt) {
        this(blackboard, trace, runId, attempt, RunPrincipal.anonymous());
    }

    public DefaultExecutionContext {
        Objects.requireNonNull(blackboard, "blackboard");
        Objects.requireNonNull(trace, "trace");
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        principal = principal == null ? RunPrincipal.anonymous() : principal;
    }
}
