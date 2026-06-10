package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.BlackboardKey;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.SuspendedRun;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcSuspendedRunRepositoryTest {
    private static final Condition INPUT_PRESENT = Condition.of("jdbc-test:INPUT_PRESENT");
    private static final Condition DRAFTED = Condition.of("jdbc-test:DRAFTED");

    @Test
    void persistsAndRestoresSuspendedRunSnapshot() {
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(JdbcTestDataSources.h2());
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));
        blackboard.addCondition(INPUT_PRESENT);

        repository.save(new SuspendedRun(
                "RUN-1",
                new Goal("persistedGoal", Set.of(DRAFTED)),
                blackboard,
                List.of(new ActionId("action.one")),
                List.of(new ActionId("action.one")),
                new ActionId("action.two"),
                "waiting"
        ));

        SuspendedRun restored = repository.findByRunId("RUN-1").orElseThrow();

        assertThat(restored.goal()).isEqualTo(new Goal("persistedGoal", Set.of(DRAFTED)));
        assertThat(restored.blackboard().conditions()).containsExactly(INPUT_PRESENT);
        assertThat(restored.blackboard().get(PersistedInput.class)).contains(new PersistedInput("C001"));
        assertThat(restored.executedActions()).containsExactly(new ActionId("action.one"));
        assertThat(restored.compensationStack()).containsExactly(new ActionId("action.one"));
        assertThat(restored.pendingActionId()).isEqualTo(new ActionId("action.two"));
        assertThat(restored.message()).isEqualTo("waiting");

        repository.delete("RUN-1");
        assertThat(repository.findByRunId("RUN-1")).isEmpty();
    }

    @Test
    void persistsAndRestoresMultipleBlackboardValuesOfTheSameType() {
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(JdbcTestDataSources.h2());
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        BlackboardKey<PersistedInput> primary = BlackboardKey.of(PersistedInput.class, "primary");
        BlackboardKey<PersistedInput> secondary = BlackboardKey.of(PersistedInput.class, "secondary");
        blackboard.put(primary, new PersistedInput("C001"));
        blackboard.put(secondary, new PersistedInput("C002"));
        blackboard.addCondition(INPUT_PRESENT);

        repository.save(new SuspendedRun(
                "RUN-2",
                new Goal("persistedGoal", Set.of(DRAFTED)),
                blackboard,
                List.of(),
                List.of(),
                new ActionId("action.two"),
                "waiting"
        ));

        SuspendedRun restored = repository.findByRunId("RUN-2").orElseThrow();

        assertThat(restored.blackboard().get(primary)).contains(new PersistedInput("C001"));
        assertThat(restored.blackboard().get(secondary)).contains(new PersistedInput("C002"));
        assertThat(restored.blackboard().getAll(PersistedInput.class))
                .containsExactly(new PersistedInput("C001"), new PersistedInput("C002"));
    }

    @Test
    void claimForResumeOnlySucceedsOnce() {
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(JdbcTestDataSources.h2());
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));
        blackboard.addCondition(INPUT_PRESENT);

        repository.save(new SuspendedRun(
                "RUN-CLAIM",
                new Goal("persistedGoal", Set.of(DRAFTED)),
                blackboard,
                List.of(new ActionId("action.one")),
                List.of(new ActionId("action.one")),
                new ActionId("action.two"),
                "waiting"
        ));

        assertThat(repository.claimForResume("RUN-CLAIM")).isPresent();
        assertThat(repository.claimForResume("RUN-CLAIM")).isEmpty();
    }

    @Test
    void exposesAndValidatesClaimTimeout() {
        assertThat(JdbcSuspendedRunRepository.DEFAULT_CLAIM_TIMEOUT).isEqualTo(Duration.ofMinutes(15));

        assertThatThrownBy(() -> new JdbcSuspendedRunRepository(JdbcTestDataSources.h2(), Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimTimeout must be positive");

        assertThatThrownBy(() -> new JdbcSuspendedRunRepository(
                JdbcTestDataSources.h2(),
                "actiongraph_suspended_run_custom_timeout",
                Duration.ofSeconds(-1)
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("claimTimeout must be positive");
    }

    public record PersistedInput(String value) {
    }
}
