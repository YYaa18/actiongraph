package com.actiongraph.llm;

public final class StructuredOutputException extends RuntimeException {
    public StructuredOutputException(String message) {
        super(message);
    }

    public StructuredOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
