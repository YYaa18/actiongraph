package com.actiongraph.controlplane.auth;

import com.actiongraph.controlplane.ControlPlaneApiException;

/**
 * Raised when a Java 8 control-plane client request fails local token
 * verification.
 */
public final class UnauthorizedControlPlaneAccessException extends ControlPlaneApiException {
    private static final long serialVersionUID = 1L;

    public UnauthorizedControlPlaneAccessException(String message) {
        super(message);
    }
}
