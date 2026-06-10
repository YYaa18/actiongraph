package com.actiongraph.console;

import java.util.List;

public record ConsoleTraceResponse(
        String runId,
        int count,
        List<ConsoleTraceEventResponse> events
) {
}
