package com.actiongraph.memory;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

public record MemoryRecord(
        String id,
        MemoryScope scope,
        String type,
        Map<String, String> attributes,
        Instant createdAt,
        Instant updatedAt
) {
    public MemoryRecord {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        Objects.requireNonNull(scope, "scope");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static MemoryRecord create(MemoryScope scope, String type, Map<String, String> attributes) {
        Instant now = Instant.now();
        return new MemoryRecord(UUID.randomUUID().toString(), scope, type, attributes, now, now);
    }

    public MemoryRecord withAttributes(Map<String, String> newAttributes) {
        return new MemoryRecord(id, scope, type, newAttributes, createdAt, Instant.now());
    }
}
