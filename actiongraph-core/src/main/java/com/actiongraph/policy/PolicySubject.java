package com.actiongraph.policy;

import java.util.Objects;
import java.util.Set;

public record PolicySubject(
        String userId,
        String tenantId,
        Set<String> roles,
        Set<String> permissions
) {
    public PolicySubject {
        if (userId == null || userId.isBlank()) {
            throw new IllegalArgumentException("userId must not be blank");
        }
        if (tenantId == null || tenantId.isBlank()) {
            throw new IllegalArgumentException("tenantId must not be blank");
        }
        roles = normalize(roles, "roles");
        permissions = normalize(permissions, "permissions");
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
}
