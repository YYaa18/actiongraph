package com.actiongraph.exception;

/**
 * Failure caused by invalid ActionGraph metadata, wiring, compatibility, or
 * deployment configuration.
 */
public class ActionGraphConfigurationException extends ActionGraphException {
    private static final long serialVersionUID = 1L;

    public ActionGraphConfigurationException(String message) {
        super(message);
    }

    public ActionGraphConfigurationException(String message, Throwable cause) {
        super(message, cause);
    }
}
