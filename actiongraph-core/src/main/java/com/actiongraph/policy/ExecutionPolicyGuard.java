package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

/**
 * Runtime policy gate evaluated immediately before an Action is executed.
 *
 * <p>This SPI is the main hook for tenant, permission, risk, limit, and
 * compliance checks. It may inspect concrete Blackboard values, but it must not
 * execute the action, mutate external business systems, or add side effects that
 * require compensation. Implementations should be safe for concurrent reuse.
 *
 * <p>Null contract: callers pass non-null action and Blackboard instances, and
 * implementations must return a non-null {@link PolicyDecision}.
 */
public interface ExecutionPolicyGuard {
    /**
     * Evaluates whether the action may execute, needs human review, or must be
     * denied.
     *
     * @param action candidate action; never {@code null}
     * @param blackboard current run Blackboard; never {@code null}
     * @return non-null policy decision
     */
    PolicyDecision evaluate(Action action, Blackboard blackboard);
}
