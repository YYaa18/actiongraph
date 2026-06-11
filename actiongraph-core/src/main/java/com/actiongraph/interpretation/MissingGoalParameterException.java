package com.actiongraph.interpretation;

import com.actiongraph.exception.ActionGraphInputException;

import java.util.Objects;

/**
 * Raised when a goal is ready to seed a Blackboard but a required parameter is
 * missing.
 */
public final class MissingGoalParameterException extends ActionGraphInputException {
    private static final long serialVersionUID = 1L;

    private final String parameterName;

    public MissingGoalParameterException(String parameterName) {
        super("Missing required goal parameter: " + parameterName);
        this.parameterName = Objects.requireNonNull(parameterName, "parameterName");
    }

    public String parameterName() {
        return parameterName;
    }
}
