package com.actiongraph.controlplane.api;

public record ControlPlaneErrorResponse(
        String error,
        String message
) {
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String CONFLICT = "CONFLICT";
    public static final String NOT_CLAIMABLE = "NOT_CLAIMABLE";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    public ControlPlaneErrorResponse {
        if (error == null || error.isBlank()) {
            throw new IllegalArgumentException("error must not be blank");
        }
        message = message == null ? "" : message;
    }

    public static ControlPlaneErrorResponse badRequest(String message) {
        return of(BAD_REQUEST, message);
    }

    public static ControlPlaneErrorResponse conflict(String message) {
        return of(CONFLICT, message);
    }

    public static ControlPlaneErrorResponse notClaimable(String message) {
        return of(NOT_CLAIMABLE, message);
    }

    public static ControlPlaneErrorResponse notFound(String message) {
        return of(NOT_FOUND, message);
    }

    public static ControlPlaneErrorResponse unauthorized(String message) {
        return of(UNAUTHORIZED, message);
    }

    public static ControlPlaneErrorResponse of(String error, String message) {
        return new ControlPlaneErrorResponse(error, message);
    }
}
