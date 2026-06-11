package com.actiongraph.runtime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

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
        runs.put(run.runId(), run);
        LOGGER.debug("Suspended run saved: runId={}, pendingActionId={}", run.runId(),
                run.pendingActionId().value());
    }

    @Override
    public Optional<SuspendedRun> findByRunId(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public Optional<SuspendedRun> claimForResume(String runId) {
        SuspendedRun claimed = runs.remove(runId);
        LOGGER.debug("Suspended run claim attempted: runId={}, claimed={}", runId, claimed != null);
        return Optional.ofNullable(claimed);
    }

    @Override
    public void delete(String runId) {
        boolean removed = runs.remove(runId) != null;
        LOGGER.debug("Suspended run delete attempted: runId={}, removed={}", runId, removed);
    }
}
