package com.actiongraph.console;

import java.util.List;

public record ConsoleRunsResponse(
        int limit,
        int offset,
        long total,
        int count,
        boolean hasMore,
        String status,
        Boolean auditComplete,
        List<ConsoleRunSummaryResponse> runs
) {
}
