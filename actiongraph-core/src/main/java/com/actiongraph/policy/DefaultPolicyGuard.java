package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public final class DefaultPolicyGuard implements ExecutionPolicyGuard {
    private final List<PermissionPolicy> permissionPolicies;

    public DefaultPolicyGuard() {
        this(new DefaultPermissionPolicy());
    }

    public DefaultPolicyGuard(PermissionPolicy permissionPolicy) {
        this(List.of(Objects.requireNonNull(permissionPolicy, "permissionPolicy")));
    }

    public DefaultPolicyGuard(PermissionPolicy... permissionPolicies) {
        this(Arrays.asList(Objects.requireNonNull(permissionPolicies, "permissionPolicies")));
    }

    public DefaultPolicyGuard(List<? extends PermissionPolicy> permissionPolicies) {
        Objects.requireNonNull(permissionPolicies, "permissionPolicies");
        if (permissionPolicies.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("permissionPolicies must not contain null");
        }
        this.permissionPolicies = List.copyOf(permissionPolicies);
    }

    @Override
    public PolicyDecision evaluate(Action action, Blackboard blackboard) {
        for (PermissionPolicy policy : permissionPolicies) {
            if (!policy.canExecute(action, blackboard)) {
                return PolicyDecision.DENY;
            }
        }
        return action.requiresHumanReview()
                ? PolicyDecision.REQUIRES_HUMAN_REVIEW
                : PolicyDecision.ALLOW;
    }
}
