package com.actiongraph.runtime;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Goal;

import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a run paused for human review.
 *
 * <p>The snapshot must contain enough state to resume with the same run id and
 * compensation semantics: the goal, Blackboard, actions already executed, the
 * compensation stack, and the pending action. Repositories may serialize this
 * record, so action ids and Blackboard value classes should be treated as
 * durable integration contracts.
 *
 * <p>Null contract: all fields are required except {@code message}, which is
 * normalized to an empty string. Lists are defensively copied.
 *
 * @param runId stable run id
 * @param goal original goal
 * @param blackboard current Blackboard snapshot
 * @param executedActions actions that have already succeeded
 * @param compensationStack action ids to compensate in reverse order if needed
 * @param pendingActionId action awaiting human review
 * @param message suspension detail for audit and operators
 */
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
