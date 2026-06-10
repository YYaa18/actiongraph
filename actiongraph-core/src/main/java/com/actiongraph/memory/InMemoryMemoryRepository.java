package com.actiongraph.memory;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class InMemoryMemoryRepository implements MemoryRepository {
    private final ConcurrentHashMap<String, MemoryRecord> records = new ConcurrentHashMap<>();

    @Override
    public void save(MemoryRecord record) {
        Objects.requireNonNull(record, "record");
        records.put(record.id(), record);
    }

    @Override
    public Optional<MemoryRecord> findById(String id) {
        Objects.requireNonNull(id, "id");
        return Optional.ofNullable(records.get(id));
    }

    @Override
    public List<MemoryRecord> findByScope(MemoryScope scope) {
        Objects.requireNonNull(scope, "scope");
        return records.values().stream()
                .filter(record -> record.scope().equals(scope))
                .sorted(Comparator.comparing(MemoryRecord::updatedAt).thenComparing(MemoryRecord::id))
                .toList();
    }

    @Override
    public List<MemoryRecord> findByScopeAndType(MemoryScope scope, String type) {
        Objects.requireNonNull(scope, "scope");
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("type must not be blank");
        }
        return findByScope(scope).stream()
                .filter(record -> record.type().equals(type))
                .toList();
    }

    @Override
    public void delete(String id) {
        Objects.requireNonNull(id, "id");
        records.remove(id);
    }
}
