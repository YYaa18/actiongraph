package com.actiongraph.exception;

/**
 * Failure caused by a concurrent or already-decided state transition.
 *
 * <p>Examples include duplicate resume callbacks and repeated human-review
 * decisions. Callers can often treat this category as idempotent retry noise
 * after checking the current run or task state.
 */
public class ActionGraphConflictException extends ActionGraphException {
    private static final long serialVersionUID = 1L;

    public ActionGraphConflictException(String message) {
        super(message);
    }

    public ActionGraphConflictException(String message, Throwable cause) {
        super(message, cause);
    }
}
