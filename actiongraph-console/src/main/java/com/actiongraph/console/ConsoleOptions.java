package com.actiongraph.console;

public record ConsoleOptions(
        String tokenHeader,
        int defaultLimit,
        int maxLimit
) {
    public static final String DEFAULT_TOKEN_HEADER = "X-ActionGraph-Console-Token";
    public static final int DEFAULT_LIMIT = 50;
    public static final int DEFAULT_MAX_LIMIT = 200;

    public ConsoleOptions {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new IllegalArgumentException("console token header must not be blank");
        }
        if (defaultLimit <= 0) {
            throw new IllegalArgumentException("console default limit must be positive");
        }
        if (maxLimit <= 0) {
            throw new IllegalArgumentException("console max limit must be positive");
        }
    }

    public static ConsoleOptions defaults() {
        return new ConsoleOptions(DEFAULT_TOKEN_HEADER, DEFAULT_LIMIT, DEFAULT_MAX_LIMIT);
    }
}
