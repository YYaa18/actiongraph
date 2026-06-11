package com.actiongraph.exception;

/**
 * Failure caused by caller-provided input that can usually be corrected and
 * retried without changing framework configuration.
 */
public class ActionGraphInputException extends ActionGraphException {
    private static final long serialVersionUID = 1L;

    public ActionGraphInputException(String message) {
        super(message);
    }

    public ActionGraphInputException(String message, Throwable cause) {
        super(message, cause);
    }
}
