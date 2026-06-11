package com.actiongraph.exception;

/**
 * Failure while talking to an external provider, database, approval system, or
 * other integration boundary owned outside the pure in-memory runtime.
 */
public class ActionGraphIntegrationException extends ActionGraphException {
    private static final long serialVersionUID = 1L;

    public ActionGraphIntegrationException(String message) {
        super(message);
    }

    public ActionGraphIntegrationException(String message, Throwable cause) {
        super(message, cause);
    }
}
