package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;

import java.util.Map;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Expected structured interpretation fields for one golden case.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public record Expectation(
        @Nullable String goalType,
        Map<String, String> parameters,
        boolean clarification,
        Set<String> missingFields,
        boolean unknownGoal
) {
    public Expectation {
        goalType = goalType == null ? "" : goalType;
        parameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        missingFields = missingFields == null ? Set.of() : Set.copyOf(missingFields);
    }
}
