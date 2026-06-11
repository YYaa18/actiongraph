package com.actiongraph.validation;

import java.util.List;
import java.util.Objects;

public record ValidationReport(boolean valid, List<GoalValidation> goals) {
    public ValidationReport {
        goals = List.copyOf(Objects.requireNonNull(goals, "goals"));
    }

    public String formatText() {
        if (valid) {
            return "ActionGraph validation passed: " + goals.size() + " goal(s) reachable.";
        }
        return goals.stream()
                .filter(goal -> !goal.reachable())
                .map(GoalValidation::diagnosticText)
                .collect(java.util.stream.Collectors.joining(System.lineSeparator()));
    }
}
