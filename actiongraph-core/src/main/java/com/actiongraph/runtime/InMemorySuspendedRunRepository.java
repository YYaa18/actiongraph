package com.actiongraph.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.actiongraph.action.ActionId;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * In-memory suspended-run repository for tests, demos, and single-process
 * development.
 *
 * <p>The implementation is thread-safe within one JVM. {@link #claimForResume(String)}
 * uses {@link ConcurrentHashMap#remove(Object)} as an atomic claim, so duplicate
 * resume attempts in the same process cannot receive the same snapshot. It is
 * not durable and must not be used as the only production persistence layer.
 */
public final class InMemorySuspendedRunRepository implements SuspendedRunRepository {
    private static final Logger LOGGER = LoggerFactory.getLogger(InMemorySuspendedRunRepository.class);
    private final ConcurrentHashMap<String, SuspendedRun> runs = new ConcurrentHashMap<>();

    @Override
    public void save(SuspendedRun run) {
        runs.put(run.runId(), pausable(run));
        LOGGER.debug("Suspended run saved: runId={}, state={}, pendingActionId={}, eventType={}, correlationId={}",
                run.runId(),
                run.snapshotState(),
                run.pendingActionId() == null ? "" : run.pendingActionId().value(),
                run.eventType() == null ? "" : run.eventType(),
                run.eventCorrelationId() == null ? "" : run.eventCorrelationId());
    }

    @Override
    public void saveCheckpoint(SuspendedRun checkpoint) {
        if (checkpoint.snapshotState() != SnapshotState.RUNNING) {
            throw new IllegalArgumentException("checkpoint must have RUNNING snapshotState");
        }
        runs.put(checkpoint.runId(), checkpoint);
        LOGGER.debug("Running checkpoint saved: runId={}, executedActions={}, inFlightActionId={}",
                checkpoint.runId(),
                checkpoint.executedActions().size(),
                checkpoint.inFlightActionId() == null ? "" : checkpoint.inFlightActionId().value());
    }

    @Override
    public Optional<SuspendedRun> findByRunId(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public Optional<SuspendedRun> claimForResume(String runId) {
        AtomicReference<SuspendedRun> claimedRef = new AtomicReference<>();
        runs.compute(runId, (id, current) -> {
            if (current == null || current.snapshotState() != SnapshotState.SUSPENDED) {
                return current;
            }
            claimedRef.set(current);
            return null;
        });
        SuspendedRun claimed = claimedRef.get();
        LOGGER.debug("Suspended run claim attempted: runId={}, claimed={}", runId, claimed != null);
        return Optional.ofNullable(claimed);
    }

    @Override
    public Optional<SuspendedRun> claimWaitingEvent(String eventType, String correlationId) {
        if (eventType == null || eventType.isBlank()) {
            throw new IllegalArgumentException("eventType must not be blank");
        }
        if (correlationId == null || correlationId.isBlank()) {
            throw new IllegalArgumentException("correlationId must not be blank");
        }
        for (String runId : runs.keySet()) {
            AtomicReference<SuspendedRun> claimedRef = new AtomicReference<>();
            runs.compute(runId, (id, current) -> {
                if (current == null || !matchesWaitingEvent(current, eventType, correlationId)) {
                    return current;
                }
                claimedRef.set(current);
                return null;
            });
            SuspendedRun claimed = claimedRef.get();
            if (claimed != null) {
                LOGGER.debug("Waiting event claimed: runId={}, eventType={}, correlationId={}",
                        claimed.runId(), eventType, correlationId);
                return Optional.of(claimed);
            }
        }
        LOGGER.debug("Waiting event claim missed: eventType={}, correlationId={}", eventType, correlationId);
        return Optional.empty();
    }

    @Override
    public Optional<SuspendedRun> claimExpiredWaiting(Instant now) {
        for (String runId : runs.keySet()) {
            AtomicReference<SuspendedRun> claimedRef = new AtomicReference<>();
            runs.compute(runId, (id, current) -> {
                if (current == null
                        || current.snapshotState() != SnapshotState.WAITING_EVENT
                        || current.eventDeadline() == null
                        || current.eventDeadline().isAfter(now)) {
                    return current;
                }
                claimedRef.set(current);
                return null;
            });
            SuspendedRun claimed = claimedRef.get();
            if (claimed != null) {
                LOGGER.debug("Expired waiting event claimed: runId={}, eventType={}, correlationId={}",
                        claimed.runId(), claimed.eventType(), claimed.eventCorrelationId());
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }

    @Override
    public boolean markInFlight(String runId, ActionId actionId) {
        AtomicReference<Boolean> updated = new AtomicReference<>(false);
        runs.computeIfPresent(runId, (id, current) -> {
            if (current.snapshotState() != SnapshotState.RUNNING) {
                return current;
            }
            updated.set(true);
            return new SuspendedRun(
                    current.runId(),
                    current.goal(),
                    current.blackboard(),
                    current.executedActions(),
                    current.compensationStack(),
                    null,
                    current.message(),
                    SnapshotState.RUNNING,
                    Instant.now(),
                    actionId
            );
        });
        return updated.get();
    }

    @Override
    public void heartbeat(String runId) {
        runs.computeIfPresent(runId, (id, current) -> {
            if (current.snapshotState() != SnapshotState.RUNNING) {
                return current;
            }
            return new SuspendedRun(
                    current.runId(),
                    current.goal(),
                    current.blackboard(),
                    current.executedActions(),
                    current.compensationStack(),
                    null,
                    current.message(),
                    SnapshotState.RUNNING,
                    Instant.now(),
                    current.inFlightActionId()
            );
        });
    }

    @Override
    public Optional<SuspendedRun> claimStaleRunning(Instant staleBefore) {
        for (String runId : runs.keySet()) {
            AtomicReference<SuspendedRun> claimedRef = new AtomicReference<>();
            runs.compute(runId, (id, current) -> {
                if (current == null
                        || current.snapshotState() != SnapshotState.RUNNING
                        || !current.heartbeatAt().isBefore(staleBefore)) {
                    return current;
                }
                claimedRef.set(current);
                return null;
            });
            SuspendedRun claimed = claimedRef.get();
            if (claimed != null) {
                LOGGER.debug("Stale running checkpoint claimed: runId={}", runId);
                return Optional.of(claimed);
            }
        }
        return Optional.empty();
    }

    @Override
    public void delete(String runId) {
        boolean removed = runs.remove(runId) != null;
        LOGGER.debug("Suspended run delete attempted: runId={}, removed={}", runId, removed);
    }

    private boolean matchesWaitingEvent(SuspendedRun run, String eventType, String correlationId) {
        return run.snapshotState() == SnapshotState.WAITING_EVENT
                && eventType.equals(run.eventType())
                && correlationId.equals(run.eventCorrelationId());
    }

    private SuspendedRun pausable(SuspendedRun run) {
        if (run.snapshotState() == SnapshotState.SUSPENDED) {
            return run;
        }
        if (run.snapshotState() == SnapshotState.WAITING_EVENT) {
            return run;
        }
        if (run.pendingActionId() == null) {
            throw new IllegalArgumentException("pendingActionId is required for suspended snapshots");
        }
        return new SuspendedRun(
                run.runId(),
                run.goal(),
                run.blackboard(),
                run.executedActions(),
                run.compensationStack(),
                run.pendingActionId(),
                run.message(),
                SnapshotState.SUSPENDED,
                Instant.now(),
                null
        );
    }
}
