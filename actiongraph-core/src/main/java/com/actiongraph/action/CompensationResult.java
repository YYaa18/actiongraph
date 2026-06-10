package com.actiongraph.action;

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
