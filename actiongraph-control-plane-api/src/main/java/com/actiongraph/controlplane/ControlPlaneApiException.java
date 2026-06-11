package com.actiongraph.controlplane;

/**
 * Base unchecked exception for the Java 8 compatible control-plane client API.
 *
 * <p>This artifact intentionally does not depend on the Java 21 runtime core,
 * so it keeps a separate lightweight exception root for old client projects.
 */
public class ControlPlaneApiException extends RuntimeException {
    private static final long serialVersionUID = 1L;

    public ControlPlaneApiException(String message) {
        super(message);
    }

    public ControlPlaneApiException(String message, Throwable cause) {
        super(message, cause);
    }
}
