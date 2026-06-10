package com.actiongraph.runtime;

import java.util.Optional;

public interface SuspendedRunRepository {
    void save(SuspendedRun run);

    Optional<SuspendedRun> findByRunId(String runId);

    void delete(String runId);
}
