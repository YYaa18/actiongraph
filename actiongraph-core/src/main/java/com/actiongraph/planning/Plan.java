package com.actiongraph.planning;

import java.util.List;
import java.util.Objects;

public record Plan(List<PlanStep> steps) {
    public Plan {
        steps = List.copyOf(Objects.requireNonNull(steps, "steps"));
    }

    public boolean isEmpty() {
        return steps.isEmpty();
    }
}
