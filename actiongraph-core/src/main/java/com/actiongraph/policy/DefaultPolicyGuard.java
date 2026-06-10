package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Objects;

public final class DefaultPolicyGuard implements ExecutionPolicyGuard {
    private final PermissionPolicy permissionPolicy;

    public DefaultPolicyGuard() {
        this(new DefaultPermissionPolicy());
    }

    public DefaultPolicyGuard(PermissionPolicy permissionPolicy) {
        this.permissionPolicy = Objects.requireNonNull(permissionPolicy, "permissionPolicy");
    }

    @Override
    public PolicyDecision evaluate(Action action, Blackboard blackboard) {
        if (!permissionPolicy.canExecute(action, blackboard)) {
            return PolicyDecision.DENY;
        }
        return action.requiresHumanReview()
                ? PolicyDecision.REQUIRES_HUMAN_REVIEW
                : PolicyDecision.ALLOW;
    }
}
