package com.actiongraph.runtime;

import com.actiongraph.action.ActionId;
import com.actiongraph.api.Experimental;
import com.actiongraph.identity.RunPrincipal;
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
 * @param eventType external event type for WAITING_EVENT snapshots
 * @param eventCorrelationId external event correlation id for WAITING_EVENT snapshots
 * @param eventDeadline wait deadline for WAITING_EVENT snapshots
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
        @Nullable ActionId inFlightActionId,
        @Experimental(
                since = "0.2.0",
                value = "External event waits are experimental until MS2 event ingress pilots complete."
        )
        @Nullable String eventType,
        @Experimental(
                since = "0.2.0",
                value = "External event waits are experimental until MS2 event ingress pilots complete."
        )
        @Nullable String eventCorrelationId,
        @Experimental(
                since = "0.2.0",
                value = "External event waits are experimental until MS2 event ingress pilots complete."
        )
        @Nullable Instant eventDeadline,
        @Experimental(
                since = "0.2.0",
                value = "Run principal snapshots are experimental until STD1 identity pilots settle."
        )
        RunPrincipal principal
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
        if (snapshotState == SnapshotState.WAITING_EVENT) {
            if (pendingActionId != null) {
                throw new IllegalArgumentException("pendingActionId must be null for WAITING_EVENT snapshots");
            }
            if (inFlightActionId != null) {
                throw new IllegalArgumentException("inFlightActionId must be null for WAITING_EVENT snapshots");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType must not be blank for WAITING_EVENT snapshots");
            }
            if (eventCorrelationId == null || eventCorrelationId.isBlank()) {
                throw new IllegalArgumentException("eventCorrelationId must not be blank for WAITING_EVENT snapshots");
            }
            eventType = eventType.trim();
            eventCorrelationId = eventCorrelationId.trim();
            Objects.requireNonNull(eventDeadline, "eventDeadline");
        } else {
            eventType = null;
            eventCorrelationId = null;
            eventDeadline = null;
        }
        if (snapshotState != SnapshotState.RUNNING && inFlightActionId != null) {
            throw new IllegalArgumentException("inFlightActionId is only valid for RUNNING checkpoints");
        }
        principal = principal == null ? RunPrincipal.anonymous() : principal;
    }

    public SuspendedRun(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActions,
            List<ActionId> compensationStack,
            @Nullable ActionId pendingActionId,
            String message,
            SnapshotState snapshotState,
            Instant heartbeatAt,
            @Nullable ActionId inFlightActionId,
            @Nullable String eventType,
            @Nullable String eventCorrelationId,
            @Nullable Instant eventDeadline
    ) {
        this(runId, goal, blackboard, executedActions, compensationStack, pendingActionId, message,
                snapshotState, heartbeatAt, inFlightActionId, eventType, eventCorrelationId, eventDeadline,
                RunPrincipal.anonymous());
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
                SnapshotState.SUSPENDED, Instant.now(), null, null, null, null, RunPrincipal.anonymous());
    }

    public SuspendedRun(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActions,
            List<ActionId> compensationStack,
            @Nullable ActionId pendingActionId,
            String message,
            SnapshotState snapshotState,
            Instant heartbeatAt,
            @Nullable ActionId inFlightActionId
    ) {
        this(runId, goal, blackboard, executedActions, compensationStack, pendingActionId, message,
                snapshotState, heartbeatAt, inFlightActionId, null, null, null, RunPrincipal.anonymous());
    }

    public SuspendedRun(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActions,
            List<ActionId> compensationStack,
            @Nullable ActionId pendingActionId,
            String message,
            SnapshotState snapshotState,
            Instant heartbeatAt,
            @Nullable ActionId inFlightActionId,
            RunPrincipal principal
    ) {
        this(runId, goal, blackboard, executedActions, compensationStack, pendingActionId, message,
                snapshotState, heartbeatAt, inFlightActionId, null, null, null, principal);
    }

    public static SuspendedRun waitingForEvent(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActions,
            List<ActionId> compensationStack,
            String message,
            String eventType,
            String eventCorrelationId,
            Instant eventDeadline
    ) {
        return waitingForEvent(runId, goal, blackboard, executedActions, compensationStack,
                message, eventType, eventCorrelationId, eventDeadline, RunPrincipal.anonymous());
    }

    public static SuspendedRun waitingForEvent(
            String runId,
            Goal goal,
            Blackboard blackboard,
            List<ActionId> executedActions,
            List<ActionId> compensationStack,
            String message,
            String eventType,
            String eventCorrelationId,
            Instant eventDeadline,
            RunPrincipal principal
    ) {
        return new SuspendedRun(runId, goal, blackboard, executedActions, compensationStack,
                null, message, SnapshotState.WAITING_EVENT, Instant.now(), null,
                eventType, eventCorrelationId, eventDeadline, principal);
    }
}
