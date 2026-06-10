package com.actiongraph.trace;

import java.util.List;

public interface TraceRepository {
    void append(TraceEvent event);

    List<TraceEvent> findByRun(String runId);
}
