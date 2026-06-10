package com.actiongraph.trace;

import java.util.Collection;
import java.util.List;

public interface TraceRepository {
    void append(TraceEvent event);

    default void appendAll(Collection<TraceEvent> events) {
        events.forEach(this::append);
    }

    List<TraceEvent> findByRun(String runId);
}
