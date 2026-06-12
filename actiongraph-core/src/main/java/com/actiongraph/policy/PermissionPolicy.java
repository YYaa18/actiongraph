package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.runtime.Blackboard;

/**
 * Fine-grained permission hook used by {@link DefaultPolicyGuard}.
 *
 * <p>Applications can compose multiple permission policies for tenant access,
 * role checks, feature flags, business-hour limits, or model/tool quotas. The
 * hook should only decide whether execution is allowed; it should not mutate the
 * Blackboard or external systems.
 *
 * <p>Null contract: callers pass non-null action and Blackboard instances.
 */
public interface PermissionPolicy {
    /**
     * Returns whether the action may execute under this policy.
     *
     * @param action candidate action; never {@code null}
     * @param blackboard current run Blackboard; never {@code null}
     * @return {@code true} to allow evaluation to continue, {@code false} to deny
     */
    default boolean canExecute(Action action, Blackboard blackboard) {
        return true;
    }

    /**
     * Returns whether the action may execute for the supplied principal.
     *
     * <p>The default delegates to the legacy two-argument hook so existing
     * policies remain source and binary compatible.
     *
     * @param action candidate action; never {@code null}
     * @param blackboard current run Blackboard; never {@code null}
     * @param principal run principal; never {@code null}
     * @return {@code true} to allow evaluation to continue, {@code false} to deny
     */
    default boolean canExecute(Action action, Blackboard blackboard, RunPrincipal principal) {
        return canExecute(action, blackboard);
    }
}
