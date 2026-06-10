package com.actiongraph.runtime;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Condition;

import java.util.List;
import java.util.Set;

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
