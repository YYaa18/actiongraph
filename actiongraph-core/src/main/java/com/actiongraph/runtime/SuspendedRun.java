package com.actiongraph.runtime;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Goal;

import java.util.List;
import java.util.Objects;

public record SuspendedRun(
        String runId,
        Goal goal,
        Blackboard blackboard,
        List<ActionId> executedActions,
        List<ActionId> compensationStack,
        ActionId pendingActionId,
        String message
) {
    public SuspendedRun {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(blackboard, "blackboard");
        executedActions = List.copyOf(Objects.requireNonNull(executedActions, "executedActions"));
        compensationStack = List.copyOf(Objects.requireNonNull(compensationStack, "compensationStack"));
        Objects.requireNonNull(pendingActionId, "pendingActionId");
        message = message == null ? "" : message;
    }
}
