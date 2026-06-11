package com.actiongraph.validation;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class ActionGraphValidator {
    private final GoapPlanner planner;

    public ActionGraphValidator() {
        this(new GoapPlanner());
    }

    public ActionGraphValidator(GoapPlanner planner) {
        this.planner = Objects.requireNonNull(planner, "planner");
    }

    public ValidationReport validate(GoalCatalog catalog, Collection<Action> actions) {
        Objects.requireNonNull(catalog, "catalog");
        Objects.requireNonNull(actions, "actions");
        List<Action> orderedActions = actions.stream()
                .sorted(Comparator.comparing(action -> action.id().value()))
                .toList();
        List<GoalValidation> goals = catalog.all().stream()
                .sorted(Comparator.comparing(definition -> definition.type().value()))
                .map(definition -> validateGoal(definition, orderedActions))
                .toList();
        return new ValidationReport(goals.stream().allMatch(GoalValidation::reachable), goals);
    }

    private GoalValidation validateGoal(GoalDefinition definition, List<Action> actions) {
        ReachabilityResult closure = ActionGraphReachability.closure(definition.seedConditions(), actions);
        Set<Condition> missingConditions = new LinkedHashSet<>(definition.goal().targetConditions());
        missingConditions.removeAll(closure.reachableConditions());

        List<ActionId> danglingActions = actions.stream()
                .filter(action -> !closure.reachableActions().contains(action.id()))
                .filter(action -> !closure.reachableConditions().containsAll(action.preconditions()))
                .map(Action::id)
                .toList();

        Optional<Plan> plan = planner.plan(definition.goal(), definition.seedConditions(), actions);
        List<PlanStep> previewPlan = plan.map(Plan::steps).orElse(List.of());
        boolean reachable = missingConditions.isEmpty() && plan.isPresent();
        List<String> diagnostics = reachable
                ? List.of()
                : diagnostics(definition, missingConditions, danglingActions, actions);
        return new GoalValidation(definition.type(), reachable, previewPlan,
                missingConditions, danglingActions, diagnostics);
    }

    private List<String> diagnostics(
            GoalDefinition definition,
            Set<Condition> missingConditions,
            List<ActionId> danglingActions,
            List<Action> actions
    ) {
        List<String> messages = new ArrayList<>();
        String missing = missingConditions.stream()
                .map(Condition::key)
                .sorted()
                .collect(java.util.stream.Collectors.joining(", "));
        if (missing.isBlank()) {
            missing = "unknown condition";
        }
        messages.add("goal '" + definition.type().value() + "' unreachable: missing condition(s) " + missing + ".");
        for (Condition condition : missingConditions.stream().sorted(Comparator.comparing(Condition::key)).toList()) {
            closestEffect(condition, actions)
                    .ifPresent(closest -> messages.add("closest registered effect for " + condition.key()
                            + " is " + closest.key() + " (possible spelling or namespace mismatch)."));
        }
        if (!danglingActions.isEmpty()) {
            messages.add("dangling action(s): " + danglingActions.stream()
                    .map(ActionId::value)
                    .sorted()
                    .collect(java.util.stream.Collectors.joining(", ")) + ".");
        }
        return List.copyOf(messages);
    }

    private Optional<Condition> closestEffect(Condition target, List<Action> actions) {
        return actions.stream()
                .flatMap(action -> action.effects().stream())
                .distinct()
                .min(Comparator
                        .comparingInt((Condition effect) -> editDistance(target.key(), effect.key()))
                        .thenComparing(Condition::key));
    }

    private int editDistance(String left, String right) {
        int[][] distance = new int[left.length() + 1][right.length() + 1];
        for (int i = 0; i <= left.length(); i++) {
            distance[i][0] = i;
        }
        for (int j = 0; j <= right.length(); j++) {
            distance[0][j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                distance[i][j] = Math.min(
                        Math.min(distance[i - 1][j] + 1, distance[i][j - 1] + 1),
                        distance[i - 1][j - 1] + cost
                );
            }
        }
        return distance[left.length()][right.length()];
    }
}
