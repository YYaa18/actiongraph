package com.actiongraph.interpretation;

import java.util.Objects;
import java.util.Optional;

public record GoalParameterDefinition(
        String name,
        String description,
        boolean required,
        Optional<String> example
) {
    public GoalParameterDefinition {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Parameter name must not be blank");
        }
        description = description == null ? "" : description;
        example = Objects.requireNonNull(example, "example");
    }

    public static GoalParameterDefinition required(String name, String description, String example) {
        return new GoalParameterDefinition(name, description, true, Optional.ofNullable(example));
    }

    public static GoalParameterDefinition optional(String name, String description, String example) {
        return new GoalParameterDefinition(name, description, false, Optional.ofNullable(example));
    }
}
