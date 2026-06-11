package com.actiongraph.runtime;

import com.actiongraph.action.ActionId;
import com.actiongraph.api.Experimental;
import com.actiongraph.planning.Goal;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

/**
 * Immutable snapshot of a run paused for human review or checkpointed for
 * crash recovery.
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
 * @param pendingActionId action awaiting human review; null for running checkpoints
 * @param message suspension detail for audit and operators
 * @param snapshotState visible snapshot state
 * @param heartbeatAt last executor heartbeat for running checkpoints
 * @param inFlightActionId action whose outcome is unknown after a crash
 */
public record SuspendedRun(
        String runId,
        Goal goal,
        Blackboard blackboard,
        List<ActionId> executedActions,
        List<ActionId> compensationStack,
        @Nullable ActionId pendingActionId,
        String message,
        @Experimental(
                since = "0.2.0",
                value = "Durable checkpoints are experimental until MS1 crash-recovery pilots complete."
        )
        SnapshotState snapshotState,
        @Experimental(
                since = "0.2.0",
                value = "Durable checkpoint heartbeats are experimental until MS1 crash-recovery pilots complete."
        )
        Instant heartbeatAt,
        @Experimental(
                since = "0.2.0",
                value = "In-flight action recovery is experimental until MS1 crash-recovery pilots complete."
        )
        @Nullable ActionId inFlightActionId
) {
    public SuspendedRun {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(goal, "goal");
        Objects.requireNonNull(blackboard, "blackboard");
        executedActions = List.copyOf(Objects.requireNonNull(executedActions, "executedActions"));
        compensationStack = List.copyOf(Objects.requireNonNull(compensationStack, "compensationStack"));
        message = message == null ? "" : message;
        snapshotState = Objects.requireNonNull(snapshotState, "snapshotState");
        heartbeatAt = Objects.requireNonNull(heartbeatAt, "heartbeatAt");
        if (snapshotState == SnapshotState.SUSPENDED) {
            Objects.requireNonNull(pendingActionId, "pendingActionId");
        }
        if (snapshotState == SnapshotState.RUNNING && pendingActionId != null) {
            throw new IllegalArgumentException("pendingActionId must be null for RUNNING checkpoints");
        }
    }

    public SuspendedRun(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActions,
            List<ActionId> compensationStack,
            ActionId pendingActionId,
            String message
    ) {
        this(runId, goal, blackboard, executedActions, compensationStack, pendingActionId, message,
                SnapshotState.SUSPENDED, Instant.now(), null);
    }
}
