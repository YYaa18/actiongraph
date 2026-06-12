package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.api.Experimental;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.runtime.Blackboard;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Minimal any-of role gate for actions.
 *
 * <p>This policy is intentionally small: it demonstrates the principal-aware
 * permission hook while leaving enterprise IAM integration to application code.
 */
@Experimental(
        since = "0.2.0",
        value = "Principal-aware role gates are experimental until STD1 authorization pilots settle."
)
public final class RolePermissionPolicy implements PermissionPolicy {
    private final PermissionPolicy delegate;
    private final Map<String, Set<String>> requiredRolesByActionId;

    public RolePermissionPolicy(Map<String, Set<String>> requiredRolesByActionId) {
        this(new DefaultPermissionPolicy(), requiredRolesByActionId);
    }

    public RolePermissionPolicy(PermissionPolicy delegate, Map<String, Set<String>> requiredRolesByActionId) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.requiredRolesByActionId = normalize(requiredRolesByActionId);
    }

    @Override
    public boolean canExecute(Action action, Blackboard blackboard) {
        return delegate.canExecute(action, blackboard);
    }

    @Override
    public boolean canExecute(Action action, Blackboard blackboard, RunPrincipal principal) {
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(blackboard, "blackboard");
        RunPrincipal safePrincipal = principal == null ? RunPrincipal.anonymous() : principal;
        if (!delegate.canExecute(action, blackboard, safePrincipal)) {
            return false;
        }
        Set<String> requiredRoles = requiredRolesByActionId.get(action.id().value());
        if (requiredRoles == null || requiredRoles.isEmpty()) {
            return true;
        }
        Set<String> actualRoles = rolesOf(safePrincipal);
        return requiredRoles.stream().anyMatch(actualRoles::contains);
    }

    public Map<String, Set<String>> requiredRolesByActionId() {
        return requiredRolesByActionId;
    }

    private static Map<String, Set<String>> normalize(Map<String, Set<String>> raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        Map<String, Set<String>> normalized = new LinkedHashMap<>();
        raw.forEach((actionId, roles) -> {
            if (actionId == null || actionId.isBlank()) {
                throw new IllegalArgumentException("action role actionId must not be blank");
            }
            Set<String> safeRoles = roles == null
                    ? Set.of()
                    : roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .map(String::trim)
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            if (safeRoles.isEmpty()) {
                throw new IllegalArgumentException("action role anyOf must not be empty for " + actionId);
            }
            normalized.put(actionId.trim(), Set.copyOf(safeRoles));
        });
        return Map.copyOf(normalized);
    }

    private static Set<String> rolesOf(RunPrincipal principal) {
        Set<String> roles = new LinkedHashSet<>();
        addRoles(roles, principal.attributes().get("roles"));
        addRoles(roles, principal.attributes().get("role"));
        return Set.copyOf(roles);
    }

    private static void addRoles(Set<String> roles, String raw) {
        if (raw == null || raw.isBlank()) {
            return;
        }
        Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .forEach(roles::add);
    }
}
