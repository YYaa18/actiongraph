package com.actiongraph.memory;

import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.BlackboardKey;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class MemoryRepositoryTest {
    @Test
    void storesAndQueriesStructuredMemoryByScopeAndType() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryScope scope = new MemoryScope("tenant-a", "customer:C001", "renewal");
        MemoryRecord preference = MemoryRecord.create(scope, "preference", Map.of("billingCycle", "annual"));
        MemoryRecord risk = MemoryRecord.create(scope, "risk", Map.of("level", "low"));
        repository.save(preference);
        repository.save(risk);

        assertThat(repository.findById(preference.id())).contains(preference);
        assertThat(repository.findByScope(scope)).containsExactlyInAnyOrder(preference, risk);
        assertThat(repository.findByScopeAndType(scope, "preference")).containsExactly(preference);

        repository.delete(preference.id());
        assertThat(repository.findById(preference.id())).isEmpty();
    }

    @Test
    void loadsMemoryContextIntoBlackboardWithOptionalConditionAndKey() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryScope scope = new MemoryScope("tenant-a", "customer:C001", "renewal");
        repository.save(MemoryRecord.create(scope, "preference", Map.of("billingCycle", "annual")));
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        BlackboardKey<MemoryContext> key = BlackboardKey.of(MemoryContext.class, "renewal");
        Condition loaded = Condition.of("memory", "CONTEXT_LOADED");

        MemoryContext context = new MemoryContextLoader(repository).load(scope, key, blackboard, loaded);

        assertThat(context.firstByType("preference")).isPresent();
        assertThat(blackboard.get(key)).contains(context);
        assertThat(blackboard.conditions()).contains(loaded);
    }
}
