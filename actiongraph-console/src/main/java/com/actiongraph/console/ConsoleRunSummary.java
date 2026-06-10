package com.actiongraph.console;

import java.time.Instant;

public record ConsoleRunSummary(
        String runId,
        Instant firstEventAt,
        Instant lastEventAt,
        String status,
        int traceEventCount,
        boolean auditComplete,
        long firstBrokenSeq,
        String auditMessage
) {
    public ConsoleRunSummary {
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
