package com.actiongraph.controlplane.api;

import org.jspecify.annotations.Nullable;

public final class ControlPlaneErrorResponse {
    public static final String BAD_REQUEST = "BAD_REQUEST";
    public static final String CONFLICT = "CONFLICT";
    public static final String FORBIDDEN = "FORBIDDEN";
    public static final String NOT_CLAIMABLE = "NOT_CLAIMABLE";
    public static final String NOT_FOUND = "NOT_FOUND";
    public static final String UNAUTHORIZED = "UNAUTHORIZED";

    private final String error;
    private final String message;

    public ControlPlaneErrorResponse(String error, @Nullable String message) {
        if (isBlank(error)) {
            throw new IllegalArgumentException("error must not be blank");
        }
        this.error = error;
        this.message = message == null ? "" : message;
    }

    public String error() {
        return error;
    }

    public String message() {
        return message;
    }

    public String getError() {
        return error;
    }

    public String getMessage() {
        return message;
    }

    public static ControlPlaneErrorResponse badRequest(@Nullable String message) {
        return of(BAD_REQUEST, message);
    }

    public static ControlPlaneErrorResponse conflict(@Nullable String message) {
        return of(CONFLICT, message);
    }

    public static ControlPlaneErrorResponse forbidden(@Nullable String message) {
        return of(FORBIDDEN, message);
    }

    public static ControlPlaneErrorResponse notClaimable(@Nullable String message) {
        return of(NOT_CLAIMABLE, message);
    }

    public static ControlPlaneErrorResponse notFound(@Nullable String message) {
        return of(NOT_FOUND, message);
    }

    public static ControlPlaneErrorResponse unauthorized(@Nullable String message) {
        return of(UNAUTHORIZED, message);
    }

    public static ControlPlaneErrorResponse of(String error, @Nullable String message) {
        return new ControlPlaneErrorResponse(error, message);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
