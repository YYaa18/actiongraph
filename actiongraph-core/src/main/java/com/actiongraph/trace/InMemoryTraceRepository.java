package com.actiongraph.trace;

import java.util.Comparator;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * In-memory trace repository for tests, demos, and local development.
 *
 * <p>The implementation is thread-safe within one JVM and preserves append
 * order for each run. It is not durable and should not be used as the only audit
 * repository in production.
 */
public final class InMemoryTraceRepository implements TraceRepository {
    private final CopyOnWriteArrayList<TraceEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void append(TraceEvent event) {
        events.add(event);
    }

    @Override
    public void appendAll(Collection<TraceEvent> events) {
        this.events.addAll(events);
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
