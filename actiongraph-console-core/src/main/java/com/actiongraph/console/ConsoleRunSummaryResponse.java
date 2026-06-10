package com.actiongraph.console;

import com.actiongraph.persistence.jdbc.TraceRunSummary;

import java.time.Instant;

public record ConsoleRunSummaryResponse(
        String runId,
        Instant firstEventAt,
        Instant lastEventAt,
        String status,
        int traceEventCount,
        boolean auditComplete,
        long firstBrokenSeq,
        String auditMessage
) {
    static ConsoleRunSummaryResponse from(TraceRunSummary summary) {
        return new ConsoleRunSummaryResponse(
                summary.runId(),
                summary.firstEventAt(),
                summary.lastEventAt(),
                summary.status(),
                summary.traceEventCount(),
                summary.auditComplete(),
                summary.firstBrokenSeq(),
                summary.auditMessage()
        );
    }
}
