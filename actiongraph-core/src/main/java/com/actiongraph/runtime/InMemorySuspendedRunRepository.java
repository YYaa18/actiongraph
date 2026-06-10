package com.actiongraph.runtime;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemorySuspendedRunRepository implements SuspendedRunRepository {
    private final ConcurrentHashMap<String, SuspendedRun> runs = new ConcurrentHashMap<>();

    @Override
    public void save(SuspendedRun run) {
        runs.put(run.runId(), run);
    }

    @Override
    public Optional<SuspendedRun> findByRunId(String runId) {
        return Optional.ofNullable(runs.get(runId));
    }

    @Override
    public Optional<SuspendedRun> claimForResume(String runId) {
        return Optional.ofNullable(runs.remove(runId));
    }

    @Override
    public void delete(String runId) {
        runs.remove(runId);
    }
}
