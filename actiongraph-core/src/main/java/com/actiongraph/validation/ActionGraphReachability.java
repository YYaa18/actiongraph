package com.actiongraph.validation;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Condition;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public final class ActionGraphReachability {
    private ActionGraphReachability() {
    }

    public static ReachabilityResult closure(Set<Condition> seedConditions, Collection<Action> actions) {
        Objects.requireNonNull(seedConditions, "seedConditions");
        Objects.requireNonNull(actions, "actions");
        Set<Condition> reachableConditions = new LinkedHashSet<>(seedConditions);
        Set<ActionId> reachableActions = new LinkedHashSet<>();
        boolean changed;
        do {
            changed = false;
            for (Action action : actions.stream()
                    .sorted(Comparator.comparing(candidate -> candidate.id().value()))
                    .toList()) {
                if (reachableActions.contains(action.id())) {
                    continue;
                }
                if (!reachableConditions.containsAll(action.preconditions())) {
                    continue;
                }
                reachableActions.add(action.id());
                changed |= reachableConditions.addAll(action.effects());
            }
        } while (changed);
        return new ReachabilityResult(reachableConditions, reachableActions.stream().toList());
    }
}
