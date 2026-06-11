package com.actiongraph.runtime;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionRegistry;
import com.actiongraph.planning.Goal;

import java.util.Collection;

/**
 * Runtime entry point for executing a goal against a set of Actions.
 *
 * <p>Implementations may plan, execute, suspend, resume, compensate, and write
 * trace records. The supplied Blackboard is mutable per-run state; callers
 * should create or restore a fresh instance for each independent run.
 *
 * <p>Thread-safety depends on the implementation and its collaborators. The
 * default executor keeps per-run state local to the invocation and can be shared
 * when the injected planner, repositories, policies, and actions are safe to
 * share.
 */
public interface Executor {
    /**
     * Executes or suspends the goal.
     *
     * @param goal target symbolic goal; never {@code null}
     * @param initial initial Blackboard; never {@code null}
     * @param actions candidate actions for this run; never {@code null}
     * @param registry action registry used for compensation and resume lookup;
     * never {@code null}
     * @return non-null run result
     */
    RunResult run(Goal goal, Blackboard initial, Collection<Action> actions, ActionRegistry registry);
}
