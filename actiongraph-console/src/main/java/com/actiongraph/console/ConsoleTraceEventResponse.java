package com.actiongraph.console;

import com.actiongraph.trace.TraceEvent;

import java.time.Instant;
import java.util.Map;

public record ConsoleTraceEventResponse(
        long seq,
        Instant at,
        String type,
        String actionId,
        String detail,
        Map<String, String> data,
        String prevHash,
        String hash
) {
    static ConsoleTraceEventResponse from(TraceEvent event) {
        return new ConsoleTraceEventResponse(
                event.seq(),
                event.at(),
                event.type().name(),
                event.actionId(),
                event.detail(),
                event.data(),
                event.prevHash(),
                event.hash()
        );
    }
}
