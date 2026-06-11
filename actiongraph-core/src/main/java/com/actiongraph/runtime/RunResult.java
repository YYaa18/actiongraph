package com.actiongraph.runtime;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Condition;

import java.util.List;
import java.util.Set;

/**
 * Terminal or suspended result returned by the runtime.
 *
 * <p>The result is an execution summary, not a substitute for trace. For audit
 * and step-level diagnostics, read {@code TraceRepository.findByRun(runId)}.
 * Collections are defensively copied.
 *
 * @param runId stable run id
 * @param status final or suspended status
 * @param finalState symbolic state when execution stopped
 * @param executedActions actions that succeeded before the result
 * @param message human-readable status detail; {@code null} becomes empty
 */
public record RunResult(
        String runId,
        RunStatus status,
        Set<Condition> finalState,
        List<ActionId> executedActions,
        String message
) {
    public RunResult {
        finalState = Set.copyOf(finalState);
        executedActions = List.copyOf(executedActions);
        message = message == null ? "" : message;
    }
}
