package com.actiongraph.interpretation;

import com.actiongraph.api.Experimental;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.Condition;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GoalDefinition(
        GoalType type,
        String description,
        Goal goal,
        List<GoalParameterDefinition> parameters,
        @Experimental(
                since = "0.1.0",
                value = "Seed-condition metadata is experimental while validation diagnostics are validated through more domains."
        )
        Set<Condition> seedConditions
) {
    public GoalDefinition(
            GoalType type,
            String description,
            Goal goal,
            List<GoalParameterDefinition> parameters
    ) {
        this(type, description, goal, parameters, Set.of());
    }

    public GoalDefinition {
        Objects.requireNonNull(type, "type");
        description = description == null ? "" : description;
        Objects.requireNonNull(goal, "goal");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        seedConditions = Set.copyOf(Objects.requireNonNull(seedConditions, "seedConditions"));
    }
}
