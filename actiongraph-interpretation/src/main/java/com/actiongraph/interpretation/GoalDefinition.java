package com.actiongraph.interpretation;

import com.actiongraph.planning.Goal;

import java.util.List;
import java.util.Objects;

public record GoalDefinition(
        GoalType type,
        String description,
        Goal goal,
        List<GoalParameterDefinition> parameters
) {
    public GoalDefinition {
        Objects.requireNonNull(type, "type");
        description = description == null ? "" : description;
        Objects.requireNonNull(goal, "goal");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
    }
}
