package com.actiongraph.console;

public final class ConsoleRunNotFoundException extends RuntimeException {
    public ConsoleRunNotFoundException(String runId) {
        super("Trace run not found: " + runId);
    }
}
