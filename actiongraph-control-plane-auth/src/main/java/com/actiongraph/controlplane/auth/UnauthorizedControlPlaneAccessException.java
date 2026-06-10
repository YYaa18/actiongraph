package com.actiongraph.controlplane.auth;

public final class UnauthorizedControlPlaneAccessException extends RuntimeException {
    public UnauthorizedControlPlaneAccessException(String message) {
        super(message);
    }
}
