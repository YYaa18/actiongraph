package com.actiongraph.interpretation;

public final class MissingGoalParameterException extends RuntimeException {
    public MissingGoalParameterException(String parameterName) {
        super("Missing required goal parameter: " + parameterName);
    }
}
