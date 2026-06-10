package com.actiongraph.planning;

import java.util.Objects;
import java.util.Set;

public record Goal(String name, Set<Condition> targetConditions) {
    public Goal {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Goal name must not be blank");
        }
        targetConditions = Set.copyOf(Objects.requireNonNull(targetConditions, "targetConditions"));
    }

    public boolean isSatisfiedBy(Set<Condition> state) {
        return state.containsAll(targetConditions);
    }
}
