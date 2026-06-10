package com.actiongraph.planning;

import com.actiongraph.action.Action;

import java.util.Collection;
import java.util.Optional;
import java.util.Set;

public interface Planner {
    Optional<Plan> plan(Goal goal, Set<Condition> currentState, Collection<Action> actions);
}
