package com.actiongraph.console;

import org.jspecify.annotations.Nullable;

public record ConsoleRunQuery(
        int limit,
        int offset,
        @Nullable String status,
        @Nullable Boolean auditComplete
) {
    public ConsoleRunQuery {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        status = status == null || status.isBlank() ? null : status;
    }
}
