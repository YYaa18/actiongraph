package com.actiongraph.memory;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

public record MemoryContext(MemoryScope scope, List<MemoryRecord> records) {
    public MemoryContext {
        Objects.requireNonNull(scope, "scope");
        records = List.copyOf(Objects.requireNonNull(records, "records"));
    }

    public List<MemoryRecord> findByType(String type) {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        return records.stream()
                .filter(record -> record.type().equals(type))
                .toList();
    }

    public Optional<MemoryRecord> firstByType(String type) {
        return findByType(type).stream().findFirst();
    }
}
