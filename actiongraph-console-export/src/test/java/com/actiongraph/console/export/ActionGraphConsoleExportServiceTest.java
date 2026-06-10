package com.actiongraph.console.export;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleOptions;
import com.actiongraph.console.ConsoleRunPage;
import com.actiongraph.console.ConsoleRunQuery;
import com.actiongraph.console.ConsoleRunRepository;
import com.actiongraph.console.ConsoleRunSummary;
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

class ActionGraphConsoleExportServiceTest {
    @Test
    void exportsRunSummariesAsCsvWithEscapingAndFiltering() {
        InMemoryConsoleRunRepository repository = new InMemoryConsoleRunRepository();
        seed(repository, "RUN-1", "2026-06-10T10:00:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"), true,
                "Trace chain is valid");
        seed(repository, "RUN-2", "2026-06-10T10:05:00Z",
                TraceEventType.RUN_ENDED, Map.of("status", "FAILED"), false,
                "Trace event 2 is missing hash data, needs review");

        String csv = exportService(repository).runsCsv(10, 0, "FAILED", false);

        assertThat(csv).isEqualTo("""
                runId,firstEventAt,lastEventAt,status,traceEventCount,auditComplete,firstBrokenSeq,auditMessage
                RUN-2,2026-06-10T10:05:00Z,2026-06-10T10:05:01Z,FAILED,2,false,2,"Trace event 2 is missing hash data, needs review"
                """);
    }

    @Test
    void exportsTraceAsCsvAndJsonl() {
        InMemoryConsoleRunRepository repository = new InMemoryConsoleRunRepository();
        seed(repository, "RUN-TRACE", "2026-06-10T10:00:00Z", TraceEventType.ACTION_SUCCEEDED,
                orderedData("message", "approved, \"fast\"", "note", "line\nbreak"), true,
                "Trace chain is valid");

        ActionGraphConsoleExportService service = exportService(repository);

        assertThat(service.traceCsv("RUN-TRACE"))
                .contains("runId,seq,at,type,actionId,detail,data,prevHash,hash")
                .contains("RUN-TRACE,1,2026-06-10T10:00:00Z,RUN_STARTED")
                .contains("\"\"message\"\":\"\"approved, \\\"\"fast\\\"\"\"\"")
                .contains("\"\"note\"\":\"\"line\\nbreak\"\"");
        assertThat(service.traceJsonl("RUN-TRACE"))
                .contains("\"runId\":\"RUN-TRACE\"")
                .contains("\"seq\":2")
                .contains("\"type\":\"ACTION_SUCCEEDED\"")
                .contains("\"message\":\"approved, \\\"fast\\\"\"")
                .contains("\"note\":\"line\\nbreak\"");
    }

    private static Map<String, String> orderedData(String firstKey, String firstValue, String secondKey, String secondValue) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(firstKey, firstValue);
        data.put(secondKey, secondValue);
        return data;
    }

    private static ActionGraphConsoleExportService exportService(InMemoryConsoleRunRepository repository) {
        return new ActionGraphConsoleExportService(
                new ActionGraphConsoleService(repository, ConsoleOptions.defaults())
        );
    }

    private static void seed(
            InMemoryConsoleRunRepository repository,
            String runId,
            String startedAt,
            TraceEventType terminalType,
            Map<String, String> terminalData,
            boolean auditComplete,
            String auditMessage
    ) {
        TraceEvent started = hashed(runId, 1, "", Instant.parse(startedAt),
                TraceEventType.RUN_STARTED, Map.of(), null);
        TraceEvent ended = auditComplete
                ? hashed(runId, 2, started.hash(), Instant.parse(startedAt).plusSeconds(1),
                terminalType, terminalData, "action.id")
                : new TraceEvent(runId, 2, Instant.parse(startedAt).plusSeconds(1), terminalType, "action.id",
                terminalType.name(), terminalData);
        repository.save(new ConsoleRunSummary(
                runId,
                started.at(),
                ended.at(),
                terminalData.getOrDefault("status", terminalType.name()),
                2,
                auditComplete,
                auditComplete ? 0 : 2,
                auditMessage
        ), List.of(started, ended));
    }

    private static TraceEvent hashed(
            String runId,
            long seq,
            String prevHash,
            Instant at,
            TraceEventType type,
            Map<String, String> data,
            String actionId
    ) {
        String hash = TraceHasher.hash(runId, seq, at, type, actionId, type.name(), data, prevHash);
        return new TraceEvent(runId, seq, at, type, actionId, type.name(), data, prevHash, hash);
    }

    static final class InMemoryConsoleRunRepository implements ConsoleRunRepository {
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
