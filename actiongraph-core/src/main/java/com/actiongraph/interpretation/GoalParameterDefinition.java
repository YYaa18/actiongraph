package com.actiongraph.interpretation;

import com.actiongraph.api.Experimental;

import java.util.Objects;
import java.util.Optional;

public record GoalParameterDefinition(
        String name,
        String description,
        boolean required,
        Optional<String> example,
        @Experimental(
                since = "0.2.0",
                value = "Goal parameter Java types are experimental while DX4 external configuration settles."
        )
        Class<?> type
) {
    public GoalParameterDefinition(
            String name,
            String description,
            boolean required,
            Optional<String> example
    ) {
        this(name, description, required, example, String.class);
    }

    public GoalParameterDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name must not be blank");
        }
        description = description == null ? "" : description;
        example = Objects.requireNonNull(example, "example");
        type = type == null ? String.class : type;
    }

    public static GoalParameterDefinition required(String name, String description, String example) {
        return new GoalParameterDefinition(name, description, true, Optional.ofNullable(example), String.class);
    }

    public static GoalParameterDefinition required(
            String name,
            String description,
            String example,
            Class<?> type
    ) {
        return new GoalParameterDefinition(name, description, true, Optional.ofNullable(example), type);
    }

    public static GoalParameterDefinition optional(String name, String description, String example) {
        return new GoalParameterDefinition(name, description, false, Optional.ofNullable(example), String.class);
    }

    public static GoalParameterDefinition optional(
            String name,
            String description,
            String example,
            Class<?> type
    ) {
        return new GoalParameterDefinition(name, description, false, Optional.ofNullable(example), type);
    }
}
