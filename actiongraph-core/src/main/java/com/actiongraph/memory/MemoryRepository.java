package com.actiongraph.memory;

import java.util.List;
import java.util.Optional;

public interface MemoryRepository {
    void save(MemoryRecord record);

    Optional<MemoryRecord> findById(String id);

    List<MemoryRecord> findByScope(MemoryScope scope);

    List<MemoryRecord> findByScopeAndType(MemoryScope scope, String type);

    void delete(String id);
}
