package com.actiongraph.validation;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Condition;

import java.util.List;
import java.util.Objects;
import java.util.Set;

public record ReachabilityResult(
        Set<Condition> reachableConditions,
        List<ActionId> reachableActions
) {
    public ReachabilityResult {
        reachableConditions = Set.copyOf(Objects.requireNonNull(reachableConditions, "reachableConditions"));
        reachableActions = List.copyOf(Objects.requireNonNull(reachableActions, "reachableActions"));
    }
}
