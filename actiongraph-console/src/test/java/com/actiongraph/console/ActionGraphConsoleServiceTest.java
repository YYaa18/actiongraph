package com.actiongraph.console;

import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceHasher;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphConsoleServiceTest {
    @Test
    void listsRunsWithDefaultPagingAndFilters() {
        InMemoryConsoleRunRepository repository = new InMemoryConsoleRunRepository();
        seed(repository, "RUN-OLDER", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        seed(repository, "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
                TraceEventType.RUN_SUSPENDED, Map.of("status", "SUSPENDED_PENDING_REVIEW"));
        seed(repository, "RUN-NEWER", "2026-06-10T10:10:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

        ActionGraphConsoleService service = service(repository, new ConsoleOptions(
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
        ActionGraphConsoleService service = service(new InMemoryConsoleRunRepository(), new ConsoleOptions(
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
        InMemoryConsoleRunRepository repository = new InMemoryConsoleRunRepository();
        seed(repository, "RUN-1", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        ActionGraphConsoleService service = service(repository, ConsoleOptions.defaults());

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
        ActionGraphConsoleService service = service(new InMemoryConsoleRunRepository(), ConsoleOptions.defaults());

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

    private static ActionGraphConsoleService service(ConsoleRunRepository repository, ConsoleOptions options) {
        return new ActionGraphConsoleService(repository, options);
    }

    private static void seed(
            InMemoryConsoleRunRepository repository,
            String runId,
            String startedAt,
            TraceEventType terminalType,
            Map<String, String> terminalData
    ) {
        TraceEvent started = hashed(runId, 1, "", Instant.parse(startedAt),
                TraceEventType.RUN_STARTED, Map.of());
        TraceEvent ended = hashed(runId, 2, started.hash(), Instant.parse(startedAt).plusSeconds(1),
                terminalType, terminalData);
        repository.save(new ConsoleRunSummary(
                runId,
                started.at(),
                ended.at(),
                terminalData.getOrDefault("status", terminalType.name()),
                2,
                true,
                0,
                "Trace chain is valid"
        ), List.of(started, ended));
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

    private static final class InMemoryConsoleRunRepository implements ConsoleRunRepository {
        private final Map<String, ConsoleRunSummary> summaries = new LinkedHashMap<>();
        private final Map<String, List<TraceEvent>> traces = new LinkedHashMap<>();

        void save(ConsoleRunSummary summary, List<TraceEvent> trace) {
            summaries.put(summary.runId(), summary);
            traces.put(summary.runId(), List.copyOf(trace));
        }

        @Override
        public ConsoleRunPage findRuns(ConsoleRunQuery query) {
            Objects.requireNonNull(query, "query");
            List<ConsoleRunSummary> matching = summaries.values()
                    .stream()
                    .filter(summary -> query.status() == null || query.status().equals(summary.status()))
                    .filter(summary -> query.auditComplete() == null
                            || query.auditComplete() == summary.auditComplete())
                    .sorted(Comparator.comparing(ConsoleRunSummary::lastEventAt).reversed())
                    .toList();
            int total = matching.size();
            int from = Math.min(query.offset(), total);
            int to = Math.min(total, from + query.limit());
            return new ConsoleRunPage(query.limit(), query.offset(), total, new ArrayList<>(matching.subList(from, to)));
        }

        @Override
        public Optional<ConsoleRunSummary> findRun(String runId) {
            return Optional.ofNullable(summaries.get(runId));
        }

        @Override
        public List<TraceEvent> findTraceEvents(String runId) {
            return traces.getOrDefault(runId, List.of());
        }
    }
}
