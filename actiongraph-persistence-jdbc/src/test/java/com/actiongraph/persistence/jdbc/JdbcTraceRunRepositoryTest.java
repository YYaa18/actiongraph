package com.actiongraph.persistence.jdbc;

import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceHasher;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JdbcTraceRunRepositoryTest {
    @Test
    void listsRecentRunsWithStatusAndAuditVerification() {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcTraceRepository traceRepository = new JdbcTraceRepository(dataSource);
        JdbcTraceRunRepository runRepository = new JdbcTraceRunRepository(dataSource);

        appendHashedRun(traceRepository, "RUN-COMPLETED", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        appendHashedRun(traceRepository, "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
                TraceEventType.RUN_SUSPENDED, Map.of("status", "SUSPENDED_PENDING_REVIEW"));

        List<TraceRunSummary> runs = runRepository.findRecentRuns(10);

        assertThat(runs)
                .extracting(TraceRunSummary::runId)
                .containsExactly("RUN-SUSPENDED", "RUN-COMPLETED");
        assertThat(runs.getFirst()).satisfies(summary -> {
            assertThat(summary.status()).isEqualTo("SUSPENDED_PENDING_REVIEW");
            assertThat(summary.traceEventCount()).isEqualTo(2);
            assertThat(summary.auditComplete()).isTrue();
            assertThat(summary.firstBrokenSeq()).isZero();
            assertThat(summary.firstEventAt()).isEqualTo(Instant.parse("2026-06-10T10:05:00Z"));
            assertThat(summary.lastEventAt()).isEqualTo(Instant.parse("2026-06-10T10:05:01Z"));
        });
        assertThat(runRepository.findRun("RUN-COMPLETED"))
                .get()
                .satisfies(summary -> {
                    assertThat(summary.status()).isEqualTo("COMPLETED");
                    assertThat(summary.auditMessage()).isEqualTo("Trace chain is valid");
                });
    }

    @Test
    void reportsInvalidAuditChainsForLegacyRows() {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcTraceRepository traceRepository = new JdbcTraceRepository(dataSource);
        traceRepository.append(new TraceEvent(
                "RUN-LEGACY",
                1,
                Instant.parse("2026-06-10T10:00:00Z"),
                TraceEventType.RUN_STARTED,
                null,
                "legacy",
                Map.of()
        ));

        TraceRunSummary summary = new JdbcTraceRunRepository(dataSource)
                .findRun("RUN-LEGACY")
                .orElseThrow();

        assertThat(summary.status()).isEqualTo("UNKNOWN");
        assertThat(summary.auditComplete()).isFalse();
        assertThat(summary.firstBrokenSeq()).isEqualTo(1);
        assertThat(summary.auditMessage()).contains("no hash");
    }

    @Test
    void returnsEmptyForMissingRunAndRejectsInvalidLimitsAndTables() {
        DataSource dataSource = JdbcTestDataSources.h2();
        JdbcTraceRunRepository repository = new JdbcTraceRunRepository(dataSource);

        assertThat(repository.findRun("MISSING")).isEmpty();
        assertThatThrownBy(() -> repository.findRecentRuns(0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> new JdbcTraceRunRepository(dataSource, "trace;drop table x"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("SQL identifier");
    }

    private void appendHashedRun(
            JdbcTraceRepository repository,
            String runId,
            String startedAt,
            TraceEventType terminalType,
            Map<String, String> terminalData
    ) {
        TraceEvent started = hashed(runId, 1, "", Instant.parse(startedAt),
                TraceEventType.RUN_STARTED, Map.of());
        TraceEvent ended = hashed(runId, 2, started.hash(), Instant.parse(startedAt).plusSeconds(1),
                terminalType, terminalData);
        repository.appendAll(List.of(started, ended));
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
