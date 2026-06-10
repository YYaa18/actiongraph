package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.planning.Goal;

import java.util.Collection;

public interface Executor {
    RunResult run(Goal goal, Blackboard initial, Collection<Action> actions, ActionRegistry registry);
}
