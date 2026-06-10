package com.actiongraph.persistence.jdbc;

import com.actiongraph.action.ActionId;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.BlackboardKey;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.runtime.SuspendedRun;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Types;
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
    void persistsCurrentSnapshotFormatVersion() throws Exception {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(dataSource);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));

        repository.save(suspendedRun("RUN-VERSION", blackboard));

        assertThat(snapshotVersion(dataSource, "RUN-VERSION"))
                .isEqualTo(JdbcSuspendedRunRepository.SNAPSHOT_FORMAT_VERSION);
    }

    @Test
    void migratesLegacyRowsWithoutSnapshotFormatVersion() throws Exception {
        DataSource dataSource = JdbcTestDataSources.h2();
        insertLegacySuspendedRunWithoutSnapshotVersion(dataSource, "RUN-LEGACY-VERSION");

        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(dataSource);

        assertThat(snapshotVersion(dataSource, "RUN-LEGACY-VERSION"))
                .isEqualTo(JdbcSuspendedRunRepository.SNAPSHOT_FORMAT_VERSION);
        SuspendedRun restored = repository.findByRunId("RUN-LEGACY-VERSION").orElseThrow();
        assertThat(restored.blackboard().get(PersistedInput.class)).contains(new PersistedInput("C001"));
    }

    @Test
    void rejectsUnsupportedSnapshotFormatVersionBeforeRestore() throws Exception {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(dataSource);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));
        repository.save(suspendedRun("RUN-UNSUPPORTED-VERSION", blackboard));
        updateSnapshotVersion(dataSource, "RUN-UNSUPPORTED-VERSION", 999);

        assertThatThrownBy(() -> repository.findByRunId("RUN-UNSUPPORTED-VERSION"))
                .isInstanceOf(UnsupportedSuspendedRunSnapshotVersionException.class)
                .hasMessageContaining("999")
                .hasMessageContaining(String.valueOf(JdbcSuspendedRunRepository.SNAPSHOT_FORMAT_VERSION));
    }

    @Test
    void claimRollsBackWhenSnapshotFormatVersionIsUnsupported() throws Exception {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(dataSource);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));
        repository.save(suspendedRun("RUN-UNSUPPORTED-CLAIM", blackboard));
        updateSnapshotVersion(dataSource, "RUN-UNSUPPORTED-CLAIM", 999);

        assertThatThrownBy(() -> repository.claimForResume("RUN-UNSUPPORTED-CLAIM"))
                .isInstanceOf(UnsupportedSuspendedRunSnapshotVersionException.class);
        assertThat(status(dataSource, "RUN-UNSUPPORTED-CLAIM")).isEqualTo("SUSPENDED");
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
    void restoresBlackboardValuesAllowedByExactClassName() {
        BlackboardTypeRegistry registry = BlackboardTypeRegistry.builder()
                .allowClass(PersistedInput.class)
                .build();
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(JdbcTestDataSources.h2(), registry);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));

        repository.save(suspendedRun("RUN-ALLOW-CLASS", blackboard));

        SuspendedRun restored = repository.findByRunId("RUN-ALLOW-CLASS").orElseThrow();
        assertThat(restored.blackboard().get(PersistedInput.class)).contains(new PersistedInput("C001"));
    }

    @Test
    void restoresBlackboardValuesAllowedByPackagePrefix() {
        BlackboardTypeRegistry registry = BlackboardTypeRegistry.builder()
                .allowPackage("com.actiongraph.persistence.jdbc")
                .build();
        JdbcSuspendedRunRepository repository = new JdbcSuspendedRunRepository(JdbcTestDataSources.h2(), registry);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));

        repository.save(suspendedRun("RUN-ALLOW-PACKAGE", blackboard));

        SuspendedRun restored = repository.findByRunId("RUN-ALLOW-PACKAGE").orElseThrow();
        assertThat(restored.blackboard().get(PersistedInput.class)).contains(new PersistedInput("C001"));
    }

    @Test
    void rejectsBlackboardTypesOutsideAllowlistDuringRestore() {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcSuspendedRunRepository writer = new JdbcSuspendedRunRepository(dataSource);
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new PersistedInput("C001"));
        writer.save(suspendedRun("RUN-DISALLOWED", blackboard));

        BlackboardTypeRegistry registry = BlackboardTypeRegistry.builder()
                .allowClassName("java.lang.String")
                .build();
        JdbcSuspendedRunRepository reader = new JdbcSuspendedRunRepository(dataSource, registry);

        assertThatThrownBy(() -> reader.findByRunId("RUN-DISALLOWED"))
                .isInstanceOf(DisallowedBlackboardTypeException.class)
                .hasMessageContaining(PersistedInput.class.getName());
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

    private SuspendedRun suspendedRun(String runId, InMemoryBlackboard blackboard) {
        return new SuspendedRun(
                runId,
                new Goal("persistedGoal", Set.of(DRAFTED)),
                blackboard,
                List.of(),
                List.of(),
                new ActionId("action.two"),
                "waiting"
        );
    }

    private int snapshotVersion(DataSource dataSource, String runId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select snapshot_version from actiongraph_suspended_run where run_id = ?"
             )) {
            statement.setString(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getInt("snapshot_version");
            }
        }
    }

    private String status(DataSource dataSource, String runId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "select status from actiongraph_suspended_run where run_id = ?"
             )) {
            statement.setString(1, runId);
            try (ResultSet resultSet = statement.executeQuery()) {
                assertThat(resultSet.next()).isTrue();
                return resultSet.getString("status");
            }
        }
    }

    private void updateSnapshotVersion(DataSource dataSource, String runId, int snapshotVersion) throws Exception {
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "update actiongraph_suspended_run set snapshot_version = ? where run_id = ?"
             )) {
            statement.setInt(1, snapshotVersion);
            statement.setString(2, runId);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    private void insertLegacySuspendedRunWithoutSnapshotVersion(DataSource dataSource, String runId) throws Exception {
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table actiongraph_suspended_run ("
                    + "run_id varchar(128) primary key,"
                    + "goal_json clob not null,"
                    + "blackboard_json clob not null,"
                    + "executed_actions_json clob not null,"
                    + "compensation_stack_json clob not null,"
                    + "pending_action_id varchar(256) not null,"
                    + "message clob not null,"
                    + "status varchar(32),"
                    + "claimed_at varchar(64),"
                    + "updated_at varchar(64) not null"
                    + ")");
        }
        String blackboardJson = """
                {
                  "conditions": ["jdbc-test:INPUT_PRESENT"],
                  "objects": [
                    {
                      "className": "%s",
                      "id": "default",
                      "value": {"value": "C001"}
                    }
                  ]
                }
                """.formatted(PersistedInput.class.getName());
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(
                     "insert into actiongraph_suspended_run "
                             + "(run_id, goal_json, blackboard_json, executed_actions_json, "
                             + "compensation_stack_json, pending_action_id, message, status, claimed_at, updated_at) "
                             + "values (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)"
             )) {
            statement.setString(1, runId);
            statement.setString(2, """
                    {"name":"persistedGoal","targetConditions":["jdbc-test:DRAFTED"]}
                    """);
            statement.setString(3, blackboardJson);
            statement.setString(4, "[]");
            statement.setString(5, "[]");
            statement.setString(6, "action.two");
            statement.setString(7, "waiting");
            statement.setString(8, "SUSPENDED");
            statement.setNull(9, Types.VARCHAR);
            statement.setString(10, "2026-06-10T00:00:00Z");
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }
    }

    public record PersistedInput(String value) {
    }
}
