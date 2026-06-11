package com.actiongraph.runtime;

import com.actiongraph.action.ActionId;
import com.actiongraph.api.Experimental;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence SPI for runs suspended while waiting for human review.
 *
 * <p>Implementations are responsible for durable storage, deployment-specific
 * retention, and concurrency control. The critical production contract is
 * {@link #claimForResume(String)}: resuming a run must be an atomic claim so
 * concurrent approval callbacks cannot execute the same pending action twice.
 *
 * <p>Thread-safety depends on the implementation. Production implementations
 * should be safe for concurrent calls from multiple service instances.
 */
public interface SuspendedRunRepository {
    /**
     * Saves or replaces the suspended snapshot for a run.
     *
     * <p>When a resume flow suspends again, this method must put the snapshot
     * back into a resumable state. Durable implementations should save the
     * Blackboard, executed action list, compensation stack, and pending action
     * together.
     *
     * @param run suspended run snapshot; never {@code null}
     */
    void save(SuspendedRun run);

    /**
     * Saves or replaces a running checkpoint for crash recovery.
     *
     * <p>Default implementations throw because durability is opt-in. Runtime
     * builders only call this method when durability has been explicitly
     * enabled.
     *
     * @param checkpoint running snapshot; never {@code null}
     */
    @Experimental(
            since = "0.2.0",
            value = "Step-level checkpoints are experimental until MS1 crash-recovery pilots complete."
    )
    default void saveCheckpoint(SuspendedRun checkpoint) {
        throw new UnsupportedOperationException("saveCheckpoint is not supported by this repository");
    }

    /**
     * Looks up a suspended snapshot without claiming it.
     *
     * @param runId run id; never blank
     * @return snapshot when currently visible
     */
    Optional<SuspendedRun> findByRunId(String runId);

    /**
     * Atomically claims a suspended run for resume.
     *
     * <p>Only one caller may receive a given snapshot. If another process is
     * already resuming, the run has completed, or the id is unknown, this method
     * returns empty. Durable implementations may support timeout-based recovery
     * for claims abandoned by a crashed process.
     *
     * @param runId run id; never blank
     * @return claimed snapshot, or empty when no snapshot can be claimed
     */
    Optional<SuspendedRun> claimForResume(String runId);

    /**
     * Records the action currently being invoked.
     *
     * @param runId run id; never blank
     * @param actionId action about to execute; never {@code null}
     * @return {@code true} when an active running checkpoint was updated
     */
    @Experimental(
            since = "0.2.0",
            value = "In-flight action recovery is experimental until MS1 crash-recovery pilots complete."
    )
    default boolean markInFlight(String runId, ActionId actionId) {
        throw new UnsupportedOperationException("markInFlight is not supported by this repository");
    }

    /**
     * Refreshes the heartbeat for an active running checkpoint.
     *
     * @param runId run id; never blank
     */
    @Experimental(
            since = "0.2.0",
            value = "Durable checkpoint heartbeats are experimental until MS1 crash-recovery pilots complete."
    )
    default void heartbeat(String runId) {
        throw new UnsupportedOperationException("heartbeat is not supported by this repository");
    }

    /**
     * Atomically claims one stale running checkpoint for recovery.
     *
     * @param staleBefore checkpoints with heartbeats before this instant are stale
     * @return claimed checkpoint, or empty when none is available
     */
    @Experimental(
            since = "0.2.0",
            value = "Crash recovery claiming is experimental until MS1 recovery pilots complete."
    )
    default Optional<SuspendedRun> claimStaleRunning(Instant staleBefore) {
        throw new UnsupportedOperationException("claimStaleRunning is not supported by this repository");
    }

    /**
     * Removes a suspended snapshot after terminal completion.
     *
     * @param runId run id; never blank
     */
    void delete(String runId);
}
