package com.actiongraph.interpretation;

import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public record GoalParameters(Map<String, String> values) {
    public GoalParameters {
        values = Map.copyOf(Objects.requireNonNull(values, "values"));
    }

    public static GoalParameters empty() {
        return new GoalParameters(Map.of());
    }

    public static GoalParameters of(Map<String, String> values) {
        return new GoalParameters(values);
    }

    public Optional<String> get(String name) {
        return Optional.ofNullable(values.get(name));
    }

    public GoalParameters merge(GoalParameters other) {
        java.util.LinkedHashMap<String, String> merged = new java.util.LinkedHashMap<>(values);
        merged.putAll(other.values());
        return new GoalParameters(merged);
    }
}
