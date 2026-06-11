package com.actiongraph.runtime.api.batch;

import com.actiongraph.interpretation.GoalInterpretation;

import java.util.Objects;

public record BatchGoalInterpretation(String itemId, GoalInterpretation interpretation) {
    public BatchGoalInterpretation {
        if (itemId == null || itemId.isBlank()) {
            throw new IllegalArgumentException("itemId must not be blank");
        }
        Objects.requireNonNull(interpretation, "interpretation");
    }
}
