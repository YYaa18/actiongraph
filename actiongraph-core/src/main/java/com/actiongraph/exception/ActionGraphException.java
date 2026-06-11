package com.actiongraph.exception;

/**
 * Base unchecked exception for ActionGraph runtime and ecosystem modules.
 *
 * <p>Applications may catch this type when they want to handle framework-level
 * failures separately from domain exceptions thrown by business services. Plain
 * Java validation errors may still use {@link IllegalArgumentException}; public
 * ActionGraph operational failures should prefer a typed subclass.
 */
public class ActionGraphException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ActionGraphException(String message) {
        super(message);
    }

    public ActionGraphException(String message, Throwable cause) {
        super(message, cause);
    }
}
