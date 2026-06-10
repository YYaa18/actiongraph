package com.actiongraph.trace;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

public final class InMemoryTraceRepository implements TraceRepository {
    private final CopyOnWriteArrayList<TraceEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(TraceEvent event) {
        events.add(event);
    }

    @Override
    public List<TraceEvent> findByRun(String runId) {
        return events.stream()
                .filter(event -> event.runId().equals(runId))
                .sorted(Comparator.comparingLong(TraceEvent::seq))
                .toList();
    }

    public List<TraceEvent> all() {
        return events.stream()
                .sorted(Comparator.comparing(TraceEvent::runId).thenComparingLong(TraceEvent::seq))
                .toList();
    }
}
