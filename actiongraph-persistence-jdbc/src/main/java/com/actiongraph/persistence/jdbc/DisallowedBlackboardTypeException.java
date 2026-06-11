package com.actiongraph.persistence.jdbc;

import com.actiongraph.exception.ActionGraphConfigurationException;

/**
 * Raised when a JDBC suspended-run snapshot contains a Blackboard type outside
 * the configured allowlist.
 */
public final class DisallowedBlackboardTypeException extends ActionGraphConfigurationException {
    private static final long serialVersionUID = 1L;

    private final String className;

    public DisallowedBlackboardTypeException(String className) {
        super("Blackboard type is not allowed for JDBC snapshot restore: " + className);
        this.className = className;
    }

    public String className() {
        return className;
    }
}
