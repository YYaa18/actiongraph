package com.actiongraph.persistence.jdbc;

import org.jspecify.annotations.Nullable;

public record TraceRunQuery(
        int limit,
        int offset,
        @Nullable String status,
        @Nullable Boolean auditComplete
) {
    public TraceRunQuery {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must not be negative");
        }
        status = status == null || status.isBlank() ? null : status;
    }

    public static TraceRunQuery recent(int limit) {
        return new TraceRunQuery(limit, 0, null, null);
    }
}
