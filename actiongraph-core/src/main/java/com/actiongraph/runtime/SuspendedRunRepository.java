package com.actiongraph.runtime;

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
     * Removes a suspended snapshot after terminal completion.
     *
     * @param runId run id; never blank
     */
    void delete(String runId);
}
