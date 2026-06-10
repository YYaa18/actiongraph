package com.actiongraph.console;

import com.actiongraph.trace.TraceEvent;

import java.util.List;
import java.util.Optional;

public interface ConsoleRunRepository {
    ConsoleRunPage findRuns(ConsoleRunQuery query);

    Optional<ConsoleRunSummary> findRun(String runId);

    List<TraceEvent> findTraceEvents(String runId);
}
