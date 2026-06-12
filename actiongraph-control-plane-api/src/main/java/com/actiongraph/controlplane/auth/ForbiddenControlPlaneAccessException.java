package com.actiongraph.controlplane.auth;

import com.actiongraph.controlplane.ControlPlaneApiException;

/**
 * Raised when a control-plane request is authenticated but lacks the required
 * endpoint scope.
 */
public final class ForbiddenControlPlaneAccessException extends ControlPlaneApiException {
    private static final long serialVersionUID = 1L;

    public ForbiddenControlPlaneAccessException(String message) {
        super(message);
    }
}
