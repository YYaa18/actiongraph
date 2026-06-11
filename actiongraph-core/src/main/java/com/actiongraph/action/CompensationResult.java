package com.actiongraph.action;

/**
 * Result returned by best-effort Action compensation.
 *
 * <p>Compensation runs after a previous action has succeeded and a later step
 * fails or is denied. A no-op result means there was nothing to undo and still
 * counts as complete. Messages are stored in trace and should not contain raw
 * sensitive data.
 *
 * @param success whether compensation completed
 * @param noOp whether no compensation work was required
 * @param message trace detail; {@code null} becomes empty
 */
public record CompensationResult(boolean success, boolean noOp, String message) {
    public CompensationResult {
        message = message == null ? "" : message;
    }

    public static CompensationResult ok(String message) {
        return new CompensationResult(true, false, message);
    }

    public static CompensationResult noop() {
        return new CompensationResult(true, true, "noop");
    }

    public static CompensationResult failed(String message) {
        return new CompensationResult(false, false, message);
    }
}
