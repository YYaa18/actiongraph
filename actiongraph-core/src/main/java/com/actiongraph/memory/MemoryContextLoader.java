package com.actiongraph.memory;

import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.BlackboardKey;

import java.util.Objects;

public final class MemoryContextLoader {
    private final MemoryRepository repository;

    public MemoryContextLoader(MemoryRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public MemoryContext load(MemoryScope scope, Blackboard blackboard) {
        return load(scope, BlackboardKey.of(MemoryContext.class), blackboard, null);
    }

    public MemoryContext load(MemoryScope scope, Blackboard blackboard, Condition loadedCondition) {
        return load(scope, BlackboardKey.of(MemoryContext.class), blackboard, loadedCondition);
    }

    public MemoryContext load(MemoryScope scope, BlackboardKey<MemoryContext> key, Blackboard blackboard) {
        return load(scope, key, blackboard, null);
    }

    public MemoryContext load(
            MemoryScope scope,
            BlackboardKey<MemoryContext> key,
            Blackboard blackboard,
            Condition loadedCondition
    ) {
        Objects.requireNonNull(scope, "scope");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(blackboard, "blackboard");
        MemoryContext context = new MemoryContext(scope, repository.findByScope(scope));
        blackboard.put(key, context);
        if (loadedCondition != null) {
            blackboard.addCondition(loadedCondition);
        }
        return context;
    }
}
