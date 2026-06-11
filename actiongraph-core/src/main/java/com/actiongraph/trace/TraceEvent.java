package com.actiongraph.trace;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

/**
 * Immutable audit event emitted by the runtime.
 *
 * <p>Events are ordered by {@link #seq()} within a run. {@link #prevHash()} and
 * {@link #hash()} form a tamper-evident chain over the masked event payload.
 * Data written here may be displayed to operators and should already be masked
 * according to the configured policy.
 *
 * <p>Null contract: required fields are validated, optional text becomes empty,
 * and {@code data == null} becomes an empty map. The data map is defensively
 * copied.
 *
 * @param runId stable run id
 * @param seq one-based sequence number within the run
 * @param at event timestamp
 * @param type event type
 * @param actionId action id when the event is action-scoped, otherwise empty
 * @param detail masked human-readable detail
 * @param data masked structured payload
 * @param prevHash previous event hash in this run, or empty for the first event
 * @param hash hash of this masked event payload
 */
public record TraceEvent(
        String runId,
        long seq,
        Instant at,
        TraceEventType type,
        String actionId,
        String detail,
        Map<String, String> data,
        String prevHash,
        String hash
) {
    public TraceEvent(
            String runId,
            long seq,
            Instant at,
            TraceEventType type,
            String actionId,
            String detail,
            Map<String, String> data
    ) {
        this(runId, seq, at, type, actionId, detail, data, "", "");
    }

    public TraceEvent {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (seq <= 0) {
            throw new IllegalArgumentException("seq must be > 0");
        }
        at = Objects.requireNonNull(at, "at");
        type = Objects.requireNonNull(type, "type");
        detail = detail == null ? "" : detail;
        data = data == null ? Map.of() : Map.copyOf(data);
        prevHash = prevHash == null ? "" : prevHash;
        hash = hash == null ? "" : hash;
    }
}
