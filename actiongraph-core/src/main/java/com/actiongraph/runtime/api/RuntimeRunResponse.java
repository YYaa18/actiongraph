package com.actiongraph.runtime.api;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.RunStatus;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

public record RuntimeRunResponse(
        String runId,
        RunStatus status,
        List<String> finalConditions,
        List<String> executedActions,
        String message
) {
    public RuntimeRunResponse {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(status, "status");
        finalConditions = List.copyOf(Objects.requireNonNull(finalConditions, "finalConditions"));
        executedActions = List.copyOf(Objects.requireNonNull(executedActions, "executedActions"));
        message = message == null ? "" : message;
    }

    public static RuntimeRunResponse from(RunResult result) {
        Objects.requireNonNull(result, "result");
        return new RuntimeRunResponse(
                result.runId(),
                result.status(),
                result.finalState().stream()
                        .sorted(Comparator.comparing(Condition::key))
                        .map(Condition::key)
                        .toList(),
                result.executedActions().stream()
                        .map(ActionId::value)
                        .toList(),
                result.message()
        );
    }
}
