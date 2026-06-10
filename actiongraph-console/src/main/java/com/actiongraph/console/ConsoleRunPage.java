package com.actiongraph.console;

import java.util.List;

public record ConsoleRunPage(
        int limit,
        int offset,
        long total,
        List<ConsoleRunSummary> runs
) {
    public ConsoleRunPage {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        if (total < 0) {
            throw new IllegalArgumentException("total must not be negative");
        }
        runs = List.copyOf(runs);
    }

    public boolean hasMore() {
        return (long) offset + runs.size() < total;
    }
}
