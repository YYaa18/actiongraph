package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.runtime.Blackboard;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

public final class RuleBasedPermissionPolicy implements PermissionPolicy {
    private final Map<ActionId, PermissionRule> rules;
    private final boolean allowUnmatchedActions;

    public RuleBasedPermissionPolicy(Collection<PermissionRule> rules) {
        this(rules, true);
    }

    public RuleBasedPermissionPolicy(Collection<PermissionRule> rules, boolean allowUnmatchedActions) {
        Objects.requireNonNull(rules, "rules");
        this.rules = rules.stream().collect(Collectors.toUnmodifiableMap(
                PermissionRule::actionId,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalArgumentException("Duplicate permission rule for action: " + left.actionId());
                }
        ));
        this.allowUnmatchedActions = allowUnmatchedActions;
    }

    @Override
    public boolean canExecute(Action action, Blackboard blackboard) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(blackboard, "blackboard");

        PermissionRule rule = rules.get(action.id());
        if (rule == null) {
            return allowUnmatchedActions;
        }

        Optional<PolicySubject> subjectOpt = blackboard.get(PolicySubject.class);
        if (subjectOpt.isEmpty()) {
            return false;
        }

        PolicySubject subject = subjectOpt.get();
        if (!subject.roles().containsAll(rule.requiredRoles())) {
            return false;
        }
        if (!subject.permissions().containsAll(rule.requiredPermissions())) {
            return false;
        }
        if (rule.requireTenantMatch()) {
            Optional<TenantScope> tenantScope = blackboard.get(TenantScope.class);
            return tenantScope
                    .map(scope -> subject.tenantId().equals(scope.tenantId()))
                    .orElse(false);
        }
        return true;
    }
}
