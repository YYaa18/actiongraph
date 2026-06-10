package com.actiongraph.policy;

import com.actiongraph.action.ActionId;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;

public record PermissionRule(
        ActionId actionId,
        Set<String> requiredRoles,
        Set<String> requiredPermissions,
        boolean requireTenantMatch
) {
    public PermissionRule {
        Objects.requireNonNull(actionId, "actionId");
        requiredRoles = normalize(requiredRoles, "requiredRoles");
        requiredPermissions = normalize(requiredPermissions, "requiredPermissions");
    }

    public static Builder forAction(String actionId) {
        return forAction(new ActionId(actionId));
    }

    public static Builder forAction(ActionId actionId) {
        return new Builder(actionId);
    }

    private static Set<String> normalize(Set<String> values, String label) {
        Objects.requireNonNull(values, label);
        values.forEach(value -> {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(label + " must not contain blank values");
            }
        });
        return Set.copyOf(values);
    }

    public static final class Builder {
        private final ActionId actionId;
        private final Set<String> requiredRoles = new LinkedHashSet<>();
        private final Set<String> requiredPermissions = new LinkedHashSet<>();
        private boolean requireTenantMatch;

        private Builder(ActionId actionId) {
            this.actionId = Objects.requireNonNull(actionId, "actionId");
        }

        public Builder requireRole(String role) {
            requiredRoles.add(requireNonBlank(role, "role"));
            return this;
        }

        public Builder requirePermission(String permission) {
            requiredPermissions.add(requireNonBlank(permission, "permission"));
            return this;
        }

        public Builder requireTenantMatch() {
            requireTenantMatch = true;
            return this;
        }

        public PermissionRule build() {
            return new PermissionRule(
                    actionId,
                    requiredRoles,
                    requiredPermissions,
                    requireTenantMatch
            );
        }

        private static String requireNonBlank(String value, String label) {
            if (value == null || value.isBlank()) {
                throw new IllegalArgumentException(label + " must not be blank");
            }
            return value;
        }
    }
}
