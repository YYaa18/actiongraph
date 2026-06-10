package com.actiongraph.trace;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;

public record TraceEvent(
        String runId,
        long seq,
        Instant at,
        TraceEventType type,
        String actionId,
        String detail,
        Map<String, String> data
) {
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
    }
}
