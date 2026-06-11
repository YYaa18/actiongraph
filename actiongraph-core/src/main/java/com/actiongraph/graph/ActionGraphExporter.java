package com.actiongraph.graph;

import com.actiongraph.action.Action;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.validation.ActionGraphReachability;
import com.actiongraph.validation.ReachabilityResult;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class ActionGraphExporter {
    public String toMermaid(Collection<Action> actions) {
        return toMermaid(actions, null, Set.of());
    }

    public String toMermaid(Collection<Action> actions, Goal goal) {
        return toMermaid(actions, goal, Set.of());
    }

    public String toMermaid(Collection<Action> actions, Goal goal, Set<Condition> seedConditions) {
        Objects.requireNonNull(actions, "actions");
        Objects.requireNonNull(seedConditions, "seedConditions");
        ReachabilityResult reachability = ActionGraphReachability.closure(seedConditions, actions);
        Set<Condition> targetConditions = goal == null ? Set.of() : goal.targetConditions();
        StringBuilder output = new StringBuilder("flowchart LR\n");
        for (Condition condition : conditions(actions, targetConditions, seedConditions)) {
            String id = conditionId(condition);
            output.append("  ").append(id).append("(\"").append(escape(condition.key())).append("\")");
            if (targetConditions.contains(condition)) {
                output.append(":::target");
            } else if (!seedConditions.isEmpty() && !reachability.reachableConditions().contains(condition)) {
                output.append(":::unreachable");
            }
            output.append('\n');
        }
        for (Action action : ordered(actions)) {
            String actionId = actionId(action);
            output.append("  ").append(actionId).append("[\"").append(escape(action.id().value()))
                    .append("\\nrisk=").append(action.riskLevel());
            if (action.requiresHumanReview()) {
                output.append("\\nhuman-review");
            }
            output.append("\"]");
            if (!seedConditions.isEmpty() && !reachability.reachableActions().contains(action.id())) {
                output.append(":::unreachable");
            }
            output.append('\n');
            for (Condition precondition : action.preconditions().stream()
                    .sorted(Comparator.comparing(Condition::key)).toList()) {
                output.append("  ").append(conditionId(precondition)).append(" --> ").append(actionId).append('\n');
            }
            for (Condition effect : action.effects().stream()
                    .sorted(Comparator.comparing(Condition::key)).toList()) {
                output.append("  ").append(actionId).append(" --> ").append(conditionId(effect)).append('\n');
            }
        }
        output.append("  classDef target fill:#dff7e8,stroke:#16844c,stroke-width:3px;\n");
        output.append("  classDef unreachable fill:#f3f4f6,stroke:#9ca3af,color:#6b7280,stroke-dasharray: 4 3;\n");
        return output.toString();
    }

    public String toDot(Collection<Action> actions) {
        Objects.requireNonNull(actions, "actions");
        StringBuilder output = new StringBuilder("digraph ActionGraph {\n");
        output.append("  rankdir=LR;\n");
        for (Condition condition : conditions(actions, Set.of(), Set.of())) {
            output.append("  \"").append(escapeDot(condition.key())).append("\" [shape=oval];\n");
        }
        for (Action action : ordered(actions)) {
            output.append("  \"").append(escapeDot(action.id().value())).append("\" [shape=box,label=\"")
                    .append(escapeDot(action.id().value())).append("\\nrisk=").append(action.riskLevel());
            if (action.requiresHumanReview()) {
                output.append("\\nhuman-review");
            }
            output.append("\"];\n");
            for (Condition precondition : action.preconditions().stream()
                    .sorted(Comparator.comparing(Condition::key)).toList()) {
                output.append("  \"").append(escapeDot(precondition.key())).append("\" -> \"")
                        .append(escapeDot(action.id().value())).append("\";\n");
            }
            for (Condition effect : action.effects().stream()
                    .sorted(Comparator.comparing(Condition::key)).toList()) {
                output.append("  \"").append(escapeDot(action.id().value())).append("\" -> \"")
                        .append(escapeDot(effect.key())).append("\";\n");
            }
        }
        output.append("}\n");
        return output.toString();
    }

    private Set<Condition> conditions(Collection<Action> actions, Set<Condition> targets, Set<Condition> seeds) {
        Set<Condition> conditions = new LinkedHashSet<>(seeds);
        conditions.addAll(targets);
        for (Action action : ordered(actions)) {
            conditions.addAll(action.preconditions());
            conditions.addAll(action.effects());
        }
        return conditions.stream()
                .sorted(Comparator.comparing(Condition::key))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private java.util.List<Action> ordered(Collection<Action> actions) {
        return actions.stream()
                .sorted(Comparator.comparing(action -> action.id().value()))
                .toList();
    }

    private String conditionId(Condition condition) {
        return "c_" + safeId(condition.key());
    }

    private String actionId(Action action) {
        return "a_" + safeId(action.id().value());
    }

    private String safeId(String value) {
        return value.chars()
                .mapToObj(ch -> Character.isLetterOrDigit(ch) ? Character.toString((char) ch) : "_")
                .collect(Collectors.joining());
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String escapeDot(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
