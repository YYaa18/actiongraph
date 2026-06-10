package com.actiongraph.memory;

public record MemoryScope(String tenantId, String subjectId, String namespace) {
    public MemoryScope {
        tenantId = requireNonBlank(tenantId, "tenantId");
        subjectId = requireNonBlank(subjectId, "subjectId");
        namespace = requireNonBlank(namespace, "namespace");
    }

    private static String requireNonBlank(String value, String label) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(label + " must not be blank");
        }
        return value;
    }
}
