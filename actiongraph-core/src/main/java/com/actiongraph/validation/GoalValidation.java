package com.actiongraph.validation;

import com.actiongraph.action.ActionId;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.PlanStep;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record GoalValidation(
        GoalType type,
        boolean reachable,
        List<PlanStep> previewPlan,
        Set<Condition> missingConditions,
        List<ActionId> danglingActions,
        List<String> diagnostics
) {
    public GoalValidation(
            GoalType type,
            boolean reachable,
            List<PlanStep> previewPlan,
            Set<Condition> missingConditions,
            List<ActionId> danglingActions
    ) {
        this(type, reachable, previewPlan, missingConditions, danglingActions, List.of());
    }

    public GoalValidation {
        Objects.requireNonNull(type, "type");
        previewPlan = List.copyOf(Objects.requireNonNull(previewPlan, "previewPlan"));
        missingConditions = Set.copyOf(Objects.requireNonNull(missingConditions, "missingConditions"));
        danglingActions = List.copyOf(Objects.requireNonNull(danglingActions, "danglingActions"));
        diagnostics = List.copyOf(Objects.requireNonNull(diagnostics, "diagnostics"));
    }

    public String diagnosticText() {
        if (reachable) {
            return "goal '" + type.value() + "' reachable with " + previewPlan.size() + " planned action(s).";
        }
        if (diagnostics.isEmpty()) {
            return "goal '" + type.value() + "' unreachable.";
        }
        return String.join(System.lineSeparator(), diagnostics);
    }
}
