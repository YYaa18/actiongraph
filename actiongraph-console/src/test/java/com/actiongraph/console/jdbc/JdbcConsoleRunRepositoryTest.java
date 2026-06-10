package com.actiongraph.console.jdbc;

import com.actiongraph.console.ConsoleRunQuery;
import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceHasher;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class JdbcConsoleRunRepositoryTest {
    @Test
    void adaptsJdbcTraceRunReadModelToConsolePort() {
        DataSource dataSource = h2();
        seed(dataSource, "RUN-OLDER", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        seed(dataSource, "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
                TraceEventType.RUN_SUSPENDED, Map.of("status", "SUSPENDED_PENDING_REVIEW"));
        seed(dataSource, "RUN-NEWER", "2026-06-10T10:10:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

        JdbcConsoleRunRepository repository = new JdbcConsoleRunRepository(dataSource);

        var completed = repository.findRuns(new ConsoleRunQuery(10, 0, "COMPLETED", true));
        var summary = repository.findRun("RUN-NEWER").orElseThrow();
        var trace = repository.findTraceEvents("RUN-NEWER");

        assertThat(completed.runs())
                .extracting(run -> run.runId())
                .containsExactly("RUN-NEWER", "RUN-OLDER");
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.auditComplete()).isTrue();
        assertThat(trace)
                .extracting(event -> event.type().name())
                .containsExactly("RUN_STARTED", "RUN_ENDED");
    }

    @Test
    void supportsPagingAndAuditFiltersThroughConsoleQuery() {
        DataSource dataSource = h2();
        seed(dataSource, "RUN-1", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        seedLegacy(dataSource, "RUN-LEGACY", "2026-06-10T10:05:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

        JdbcConsoleRunRepository repository = new JdbcConsoleRunRepository(dataSource);

        var audited = repository.findRuns(new ConsoleRunQuery(1, 0, "COMPLETED", true));
        var legacy = repository.findRuns(new ConsoleRunQuery(10, 0, null, false));

        assertThat(audited.total()).isEqualTo(1);
        assertThat(audited.hasMore()).isFalse();
        assertThat(audited.runs())
                .extracting(run -> run.runId())
                .containsExactly("RUN-1");
        assertThat(legacy.runs())
                .extracting(run -> run.runId())
                .containsExactly("RUN-LEGACY");
        assertThat(legacy.runs().getFirst().auditMessage()).contains("pre-F0");
    }

    private static DataSource h2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        return dataSource;
    }

    private static void seed(
            DataSource dataSource,
            String runId,
            String startedAt,
            TraceEventType terminalType,
            Map<String, String> terminalData
    ) {
        JdbcTraceRepository repository = new JdbcTraceRepository(dataSource);
        TraceEvent started = hashed(runId, 1, "", Instant.parse(startedAt),
                TraceEventType.RUN_STARTED, Map.of());
        TraceEvent ended = hashed(runId, 2, started.hash(), Instant.parse(startedAt).plusSeconds(1),
                terminalType, terminalData);
        repository.appendAll(List.of(started, ended));
    }

    private static void seedLegacy(
            DataSource dataSource,
            String runId,
            String at,
            TraceEventType type,
            Map<String, String> data
    ) {
        new JdbcTraceRepository(dataSource).append(new TraceEvent(
                runId,
                1,
                Instant.parse(at),
                type,
                null,
                "legacy",
                data
        ));
    }

    private static TraceEvent hashed(
            String runId,
            long seq,
            String prevHash,
            Instant at,
            TraceEventType type,
            Map<String, String> data
    ) {
        String hash = TraceHasher.hash(runId, seq, at, type, null, type.name(), data, prevHash);
        return new TraceEvent(runId, seq, at, type, null, type.name(), data, prevHash, hash);
    }
}
