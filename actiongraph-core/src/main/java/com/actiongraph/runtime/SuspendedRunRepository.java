package com.actiongraph.runtime;

import java.util.Optional;

public interface SuspendedRunRepository {
    void save(SuspendedRun run);

    Optional<SuspendedRun> findByRunId(String runId);

    Optional<SuspendedRun> claimForResume(String runId);

    void delete(String runId);
}
