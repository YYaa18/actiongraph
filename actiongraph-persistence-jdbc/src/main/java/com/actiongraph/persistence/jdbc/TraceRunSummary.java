package com.actiongraph.persistence.jdbc;

import java.time.Instant;

public record TraceRunSummary(
        String runId,
        Instant firstEventAt,
        Instant lastEventAt,
        String status,
        int traceEventCount,
        boolean auditComplete,
        long firstBrokenSeq,
        String auditMessage
) {
    public TraceRunSummary {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (traceEventCount < 0) {
            throw new IllegalArgumentException("traceEventCount must not be negative");
        }
        status = status == null || status.isBlank() ? "UNKNOWN" : status;
        auditMessage = auditMessage == null ? "" : auditMessage;
    }
}
