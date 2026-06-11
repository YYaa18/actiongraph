package com.actiongraph.trace;

import java.util.Collection;
import java.util.List;

/**
 * Append-only audit event repository.
 *
 * <p>The runtime writes trace events in run sequence order. Durable
 * implementations should preserve the order of {@link #appendAll(Collection)}
 * and commit a batch atomically when possible, especially before saving a
 * suspended-run snapshot. Implementations must not recalculate or strip event
 * hashes.
 *
 * <p>Thread-safety depends on the implementation. Production repositories
 * should support concurrent appends from multiple runs and deterministic reads
 * ordered by {@link TraceEvent#seq()}.
 */
public interface TraceRepository {
    /**
     * Appends a single event.
     *
     * @param event trace event; never {@code null}
     */
    void append(TraceEvent event);

    /**
     * Appends events in their supplied order.
     *
     * <p>The default implementation delegates to {@link #append(TraceEvent)}.
     * Durable implementations are encouraged to override this for one
     * transaction or database batch.
     *
     * @param events events to append; never {@code null}
     */
    default void appendAll(Collection<TraceEvent> events) {
        events.forEach(this::append);
    }

    /**
     * Returns all events for a run ordered by sequence.
     *
     * @param runId run id; never blank
     * @return non-null list, possibly empty
     */
    List<TraceEvent> findByRun(String runId);
}
