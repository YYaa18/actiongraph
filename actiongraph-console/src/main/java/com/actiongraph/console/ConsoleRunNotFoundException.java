package com.actiongraph.console;

import com.actiongraph.exception.ActionGraphNotFoundException;

import java.util.Objects;

/**
 * Raised when the read-only console cannot find a requested run.
 */
public final class ConsoleRunNotFoundException extends ActionGraphNotFoundException {
    private static final long serialVersionUID = 1L;

    private final String runId;

    public ConsoleRunNotFoundException(String runId) {
        super("trace run", runId, "Trace run not found: " + runId);
        this.runId = Objects.requireNonNull(runId, "runId");
    }

    public String runId() {
        return runId;
    }
}
