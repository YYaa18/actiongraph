package com.actiongraph.action;

import com.actiongraph.api.Experimental;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.trace.TraceRepository;

/**
 * Per-run execution context passed to Actions and compensations.
 *
 * <p>The context is scoped to one run and should not be retained by long-lived
 * application objects after the action returns. It exposes the current
 * Blackboard, trace repository, and run id for correlation.
 */
public interface ExecutionContext {
    /**
     * Current run Blackboard. It is mutable and scoped to this run; applications
     * should not share it across independent runs or threads.
     *
     * @return current Blackboard, never {@code null}
     */
    Blackboard blackboard();

    /**
     * Trace repository for the current runtime. Action code should prefer
     * returning {@link ActionResult} details over writing ad hoc trace events,
     * but the repository is exposed for advanced integrations.
     *
     * @return trace repository, never {@code null}
     */
    TraceRepository trace();

    /**
     * Stable run id used by trace, suspended-run snapshots, and external
     * review callbacks.
     *
     * @return non-blank run id
     */
    String runId();

    /**
     * One-based attempt number for the current action invocation.
     *
     * <p>The default preserves source and binary compatibility for custom
     * contexts. The built-in executor injects the real attempt number when an
     * action declares a retry policy.
     *
     * @return one-based attempt number
     */
    @Experimental(
            since = "0.2.0",
            value = "Attempt-aware execution context is experimental until idempotency conventions settle."
    )
    default int attempt() {
        return 1;
    }

    /**
     * Principal on whose behalf this run executes.
     *
     * @return non-null run principal
     */
    @Experimental(
            since = "0.2.0",
            value = "Run principal propagation is experimental until STD1 identity pilots settle."
    )
    default RunPrincipal principal() {
        return RunPrincipal.anonymous();
    }
}
