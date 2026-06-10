package com.actiongraph.persistence.jdbc;

import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceChainVerifier;
import com.actiongraph.trace.TraceHasher;
import com.actiongraph.trace.TraceEventType;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTraceRepositoryTest {
    @Test
    void persistsTraceEventsInSequenceOrder() {
        JdbcTraceRepository repository = new JdbcTraceRepository(JdbcTestDataSources.h2());
        repository.append(new TraceEvent(
                "RUN-1",
                2,
                Instant.parse("2026-01-01T00:00:02Z"),
                TraceEventType.ACTION_SUCCEEDED,
                "action.two",
                "second",
                Map.of("k2", "v2")
        ));
        repository.append(new TraceEvent(
                "RUN-1",
                1,
                Instant.parse("2026-01-01T00:00:01Z"),
                TraceEventType.RUN_STARTED,
                null,
                "first",
                Map.of("k1", "v1")
        ));

        assertThat(repository.findByRun("RUN-1"))
                .extracting(TraceEvent::seq)
                .containsExactly(1L, 2L);
        assertThat(repository.findByRun("RUN-1").get(1))
                .satisfies(event -> {
                    assertThat(event.type()).isEqualTo(TraceEventType.ACTION_SUCCEEDED);
                    assertThat(event.actionId()).isEqualTo("action.two");
                    assertThat(event.data()).containsEntry("k2", "v2");
                });
    }

    @Test
    void rejectsUnsafeTableNames() {
        assertThatThrownBy(() -> new JdbcTraceRepository(JdbcTestDataSources.h2(), "trace;drop table x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL identifier");
    }

    @Test
    void appendsTraceEventsInBatch() {
        JdbcTraceRepository repository = new JdbcTraceRepository(JdbcTestDataSources.h2());

        repository.appendAll(List.of(
                new TraceEvent(
                        "RUN-BATCH",
                        1,
                        Instant.parse("2026-01-01T00:00:01Z"),
                        TraceEventType.RUN_STARTED,
                        null,
                        "first",
                        Map.of()
                ),
                new TraceEvent(
                        "RUN-BATCH",
                        2,
                        Instant.parse("2026-01-01T00:00:02Z"),
                        TraceEventType.RUN_ENDED,
                        null,
                        "done",
                        Map.of("status", "COMPLETED")
                )
        ));

        assertThat(repository.findByRun("RUN-BATCH"))
                .extracting(TraceEvent::type)
                .containsExactly(TraceEventType.RUN_STARTED, TraceEventType.RUN_ENDED);
    }

    @Test
    void persistsTraceHashesAndDetectsTampering() throws Exception {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcTraceRepository repository = new JdbcTraceRepository(dataSource);
        TraceEvent first = hashed("RUN-HASH", 1, "", TraceEventType.RUN_STARTED, "first", Map.of());
        TraceEvent second = hashed("RUN-HASH", 2, first.hash(), TraceEventType.RUN_ENDED,
                "done", Map.of("status", "COMPLETED"));

        repository.appendAll(List.of(first, second));

        List<TraceEvent> stored = repository.findByRun("RUN-HASH");
        assertThat(stored)
                .extracting(TraceEvent::prevHash)
                .containsExactly("", first.hash());
        assertThat(stored)
                .extracting(TraceEvent::hash)
                .containsExactly(first.hash(), second.hash());
        assertThat(new TraceChainVerifier().verify(stored).valid()).isTrue();

        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("update " + JdbcTraceRepository.DEFAULT_TABLE
                    + " set detail = 'tampered' where run_id = 'RUN-HASH' and seq = 2");
        }

        var verification = new TraceChainVerifier().verify(repository.findByRun("RUN-HASH"));
        assertThat(verification.valid()).isFalse();
        assertThat(verification.firstBrokenSeq()).isEqualTo(2);
    }

    @Test
    void migratesLegacyTraceTableAndMarksOldRowsInvalid() throws Exception {
        DataSource dataSource = JdbcTestDataSources.h2();
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute("create table " + JdbcTraceRepository.DEFAULT_TABLE + " ("
                    + "run_id varchar(128) not null,"
                    + "seq bigint not null,"
                    + "at varchar(64) not null,"
                    + "type varchar(64) not null,"
                    + "action_id varchar(256),"
                    + "detail clob not null,"
                    + "data_json clob not null,"
                    + "primary key (run_id, seq)"
                    + ")");
            statement.executeUpdate("insert into " + JdbcTraceRepository.DEFAULT_TABLE
                    + " (run_id, seq, at, type, action_id, detail, data_json) values "
                    + "('RUN-OLD', 1, '2026-01-01T00:00:01Z', 'RUN_STARTED', null, 'old', '{}')");
        }

        JdbcTraceRepository repository = new JdbcTraceRepository(dataSource);
        List<TraceEvent> oldEvents = repository.findByRun("RUN-OLD");

        assertThat(oldEvents).singleElement().satisfies(event -> {
            assertThat(event.prevHash()).isEmpty();
            assertThat(event.hash()).isEmpty();
        });
        var verification = new TraceChainVerifier().verify(oldEvents);
        assertThat(verification.valid()).isFalse();
        assertThat(verification.firstBrokenSeq()).isEqualTo(1);
        assertThat(verification.message()).contains("pre-F0");
    }

    private static TraceEvent hashed(
            String runId,
            long seq,
            String prevHash,
            TraceEventType type,
            String detail,
            Map<String, String> data
    ) {
        Instant at = Instant.parse("2026-01-01T00:00:0" + seq + "Z");
        String hash = TraceHasher.hash(runId, seq, at, type, null, detail, data, prevHash);
        return new TraceEvent(runId, seq, at, type, null, detail, data, prevHash, hash);
    }
}
