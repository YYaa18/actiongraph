package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.runtime.Blackboard;

import java.util.Objects;

/**
 * Context passed to custom goal value converters.
 */
@Experimental(
        since = "0.2.0",
        value = "Goal parameter binding context is experimental until annotated seeding pilots settle."
)
public record GoalParameterBindingContext(
        String parameterName,
        Class<?> targetType,
        GoalParameters parameters,
        Blackboard blackboard
) {
    public GoalParameterBindingContext {
        if (parameterName == null || parameterName.isBlank()) {
            throw new IllegalArgumentException("parameterName must not be blank");
        }
        targetType = Objects.requireNonNull(targetType, "targetType");
        parameters = Objects.requireNonNull(parameters, "parameters");
        blackboard = Objects.requireNonNull(blackboard, "blackboard");
    }
}
