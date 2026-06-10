package com.actiongraph.console;

import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphConsoleServiceTest {
    @Test
    void listsRunsWithDefaultPagingAndFilters() {
        DataSource dataSource = h2();
        seed(dataSource, "RUN-OLDER", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        seed(dataSource, "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
                TraceEventType.RUN_SUSPENDED, Map.of("status", "SUSPENDED_PENDING_REVIEW"));
        seed(dataSource, "RUN-NEWER", "2026-06-10T10:10:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

        ActionGraphConsoleService service = service(dataSource, new ConsoleOptions(
                ConsoleOptions.DEFAULT_TOKEN_HEADER,
                2,
                10
        ));

        ConsoleRunsResponse response = service.recentRuns(null, null, "COMPLETED", true);

        assertThat(response.limit()).isEqualTo(2);
        assertThat(response.offset()).isZero();
        assertThat(response.total()).isEqualTo(2);
        assertThat(response.count()).isEqualTo(2);
        assertThat(response.hasMore()).isFalse();
        assertThat(response.status()).isEqualTo("COMPLETED");
        assertThat(response.auditComplete()).isTrue();
        assertThat(response.runs())
                .extracting(ConsoleRunSummaryResponse::runId)
                .containsExactly("RUN-NEWER", "RUN-OLDER");
    }

    @Test
    void validatesLimitAndOffset() {
        ActionGraphConsoleService service = service(h2(), new ConsoleOptions(
                ConsoleOptions.DEFAULT_TOKEN_HEADER,
                5,
                5
        ));

        assertThatThrownBy(() -> service.recentRuns(0, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must be positive");
        assertThatThrownBy(() -> service.recentRuns(6, null, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("limit must not exceed 5");
        assertThatThrownBy(() -> service.recentRuns(null, -1, null, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("offset must not be negative");
    }

    @Test
    void returnsRunSummaryAndTraceEvents() {
        DataSource dataSource = h2();
        seed(dataSource, "RUN-1", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        ActionGraphConsoleService service = service(dataSource, ConsoleOptions.defaults());

        ConsoleRunSummaryResponse summary = service.run("RUN-1");
        ConsoleTraceResponse trace = service.trace("RUN-1");

        assertThat(summary.runId()).isEqualTo("RUN-1");
        assertThat(summary.status()).isEqualTo("COMPLETED");
        assertThat(summary.auditComplete()).isTrue();
        assertThat(trace.runId()).isEqualTo("RUN-1");
        assertThat(trace.count()).isEqualTo(2);
        assertThat(trace.events())
                .extracting(ConsoleTraceEventResponse::type)
                .containsExactly("RUN_STARTED", "RUN_ENDED");
        assertThat(trace.events().get(1).data()).containsEntry("status", "COMPLETED");
        assertThat(trace.events().get(1).hash()).isNotBlank();
    }

    @Test
    void raisesTypedNotFoundForMissingRun() {
        ActionGraphConsoleService service = service(h2(), ConsoleOptions.defaults());

        assertThatThrownBy(() -> service.run("MISSING"))
                .isInstanceOf(ConsoleRunNotFoundException.class)
                .hasMessage("Trace run not found: MISSING");
        assertThatThrownBy(() -> service.trace("MISSING"))
                .isInstanceOf(ConsoleRunNotFoundException.class)
                .hasMessage("Trace run not found: MISSING");
    }

    @Test
    void rendersConsoleTemplateWithEscapedJavaScriptConfiguration() {
        String template = """
                tokenHeader: __ACTIONGRAPH_CONSOLE_TOKEN_HEADER__
                defaultLimit: __ACTIONGRAPH_CONSOLE_DEFAULT_LIMIT__
                maxLimit: __ACTIONGRAPH_CONSOLE_MAX_LIMIT__
                """;

        String rendered = ConsolePageRenderer.render(template, new ConsoleOptions(
                "X-'Console-<Token>&",
                25,
                75
        ));

        assertThat(rendered).contains("tokenHeader: 'X-\\'Console-\\u003cToken\\u003e\\u0026'");
        assertThat(rendered).contains("defaultLimit: 25");
        assertThat(rendered).contains("maxLimit: 75");
    }

    private static ActionGraphConsoleService service(DataSource dataSource, ConsoleOptions options) {
        return new ActionGraphConsoleService(new JdbcTraceRunRepository(dataSource), options);
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
