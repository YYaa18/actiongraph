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
                since = "0.2.0",
                value = "Schema metadata is experimental while DX3 automatic seeding conventions settle."
        )
        Class<?> schema,
        @Experimental(
                since = "0.1.0",
                value = "Seed-condition metadata is experimental while validation diagnostics are validated through more domains."
        )
        Set<Condition> seedConditions,
        @Experimental(
                since = "0.2.0",
                value = "Parameter seeding is experimental while DX4 external goal configuration conventions settle."
        )
        boolean parameterSeeding
) {
    public GoalDefinition(
            GoalType type,
            String description,
            Goal goal,
            List<GoalParameterDefinition> parameters
    ) {
        this(type, description, goal, parameters, Void.class, Set.of(), false);
    }

    public GoalDefinition(
            GoalType type,
            String description,
            Goal goal,
            List<GoalParameterDefinition> parameters,
            Set<Condition> seedConditions
    ) {
        this(type, description, goal, parameters, Void.class, seedConditions, false);
    }

    public GoalDefinition(
            GoalType type,
            String description,
            Goal goal,
            List<GoalParameterDefinition> parameters,
            Class<?> schema,
            Set<Condition> seedConditions
    ) {
        this(type, description, goal, parameters, schema, seedConditions, false);
    }

    public GoalDefinition {
        Objects.requireNonNull(type, "type");
        description = description == null ? "" : description;
        Objects.requireNonNull(goal, "goal");
        parameters = List.copyOf(Objects.requireNonNull(parameters, "parameters"));
        schema = schema == null ? Void.class : schema;
        seedConditions = Set.copyOf(Objects.requireNonNull(seedConditions, "seedConditions"));
    }

    @Experimental(
            since = "0.2.0",
            value = "Parameter-seeded goal definitions are experimental while DX4 external goal configuration settles."
    )
    public GoalDefinition withParameterSeeding() {
        return new GoalDefinition(type, description, goal, parameters, schema, seedConditions, true);
    }
}
