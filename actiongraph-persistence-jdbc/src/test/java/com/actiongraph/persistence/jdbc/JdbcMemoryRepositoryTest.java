package com.actiongraph.persistence.jdbc;

import com.actiongraph.memory.MemoryContext;
import com.actiongraph.memory.MemoryContextLoader;
import com.actiongraph.memory.MemoryRecord;
import com.actiongraph.memory.MemoryScope;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcMemoryRepositoryTest {
    @Test
    void persistsAndQueriesStructuredMemory() {
        JdbcMemoryRepository repository = new JdbcMemoryRepository(JdbcTestDataSources.h2());
        MemoryScope renewalScope = new MemoryScope("tenant-a", "customer:C001", "renewal");
        MemoryScope otherScope = new MemoryScope("tenant-a", "customer:C002", "renewal");
        MemoryRecord preference = MemoryRecord.create(
                renewalScope,
                "preference",
                Map.of("billingCycle", "annual")
        );
        MemoryRecord risk = MemoryRecord.create(
                renewalScope,
                "risk",
                Map.of("level", "low")
        );
        MemoryRecord other = MemoryRecord.create(
                otherScope,
                "preference",
                Map.of("billingCycle", "monthly")
        );

        repository.save(preference);
        repository.save(risk);
        repository.save(other);

        assertThat(repository.findById(preference.id())).contains(preference);
        assertThat(repository.findByScope(renewalScope)).containsExactlyInAnyOrder(preference, risk);
        assertThat(repository.findByScopeAndType(renewalScope, "preference")).containsExactly(preference);

        MemoryRecord updated = preference.withAttributes(Map.of("billingCycle", "quarterly"));
        repository.save(updated);

        assertThat(repository.findById(preference.id())).contains(updated);
        repository.delete(preference.id());
        assertThat(repository.findById(preference.id())).isEmpty();
    }

    @Test
    void memoryContextLoaderCanUseJdbcRepository() {
        JdbcMemoryRepository repository = new JdbcMemoryRepository(JdbcTestDataSources.h2());
        MemoryScope scope = new MemoryScope("tenant-a", "customer:C001", "renewal");
        repository.save(MemoryRecord.create(scope, "preference", Map.of("billingCycle", "annual")));
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        MemoryContext context = new MemoryContextLoader(repository).load(
                scope,
                blackboard,
                Condition.of("memory", "CONTEXT_LOADED")
        );

        assertThat(context.firstByType("preference")).isPresent();
        assertThat(blackboard.get(MemoryContext.class)).contains(context);
        assertThat(blackboard.conditions()).contains(Condition.of("memory", "CONTEXT_LOADED"));
    }
}
