package com.actiongraph.runtime.api.batch;

import com.actiongraph.interpretation.GoalParameters;

import java.util.Objects;

public record BatchGoalInput(String itemId, String input, GoalParameters knownParameters) {
    public BatchGoalInput {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        knownParameters = knownParameters == null ? GoalParameters.empty() : knownParameters;
        Objects.requireNonNull(knownParameters, "knownParameters");
    }

    public static BatchGoalInput of(String itemId, String input) {
        return new BatchGoalInput(itemId, input, GoalParameters.empty());
    }

    public static BatchGoalInput of(String itemId, String input, GoalParameters knownParameters) {
        return new BatchGoalInput(itemId, input, knownParameters);
    }
}
