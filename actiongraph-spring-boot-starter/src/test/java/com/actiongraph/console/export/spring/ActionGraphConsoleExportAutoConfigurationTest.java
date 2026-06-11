package com.actiongraph.console.export.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleRunPage;
import com.actiongraph.console.ConsoleRunQuery;
import com.actiongraph.console.ConsoleRunRepository;
import com.actiongraph.console.ConsoleRunSummary;
import com.actiongraph.console.export.ActionGraphConsoleExportService;
import com.actiongraph.console.spring.ActionGraphConsoleApiController;
import com.actiongraph.console.spring.ActionGraphConsolePageController;
import com.actiongraph.console.spring.ActionGraphConsoleServiceAutoConfiguration;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceHasher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphConsoleExportAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphConsoleServiceAutoConfiguration.class,
                    ActionGraphConsoleExportAutoConfiguration.class
            ));

    @Test
    void consoleExportEndpointIsDisabledByDefault() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleExportService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleExportController.class);
                });
    }

    @Test
    void consoleExportEndpointIsNotCreatedWithoutRunRepositoryOrService() {
        contextRunner
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleExportService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleExportController.class);
                });
    }

    @Test
    void consoleExportAutoConfigurationDoesNotCreateApiOrPageControllers() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsoleExportController.class);
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleApiController.class);
                    assertThat(context).doesNotHaveBean(ActionGraphConsolePageController.class);
                });
    }

    @Test
    void exportsRunsCsvAndRequiresConfiguredToken() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.path=/internal/actiongraph-console",
                        "actiongraph.console.token-header=X-Internal-Console-Token",
                        "actiongraph.console.shared-secret=console-secret"
                )
                .run(context -> {
                    InMemoryConsoleRunRepository repository = context.getBean(InMemoryConsoleRunRepository.class);
                    seed(repository, "RUN-COMPLETED", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"), true);
                    seed(repository, "RUN-FAILED", "2026-06-10T10:05:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "FAILED"), false);

                    MockMvc mockMvc = mockMvc(context);
                    mockMvc.perform(get("/internal/actiongraph-console/runs/export.csv"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    mockMvc.perform(get("/internal/actiongraph-console/runs/export.csv")
                                    .queryParam("status", "FAILED")
                                    .queryParam("auditComplete", "false")
                                    .header("X-Internal-Console-Token", "console-secret")
                                    .accept("text/csv"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentTypeCompatibleWith("text/csv"))
                            .andExpect(header().string("Content-Disposition", containsString("actiongraph-runs.csv")))
                            .andExpect(content().string(containsString(
                                    "runId,firstEventAt,lastEventAt,status,traceEventCount,auditComplete"
                            )))
                            .andExpect(content().string(containsString("RUN-FAILED")))
                            .andExpect(content().string(org.hamcrest.Matchers.not(containsString("RUN-COMPLETED"))));
                });
    }

    @Test
    void exportsTraceCsvAndJsonl() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    InMemoryConsoleRunRepository repository = context.getBean(InMemoryConsoleRunRepository.class);
                    seed(repository, "RUN.TRACE", "2026-06-10T10:00:00Z",
                            TraceEventType.ACTION_SUCCEEDED,
                            orderedData("message", "approved, \"fast\"", "status", "COMPLETED"),
                            true);

                    MockMvc mockMvc = mockMvc(context);
                    mockMvc.perform(get("/actiongraph/console/runs/RUN.TRACE/trace/export.csv")
                                    .accept("text/csv"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentTypeCompatibleWith("text/csv"))
                            .andExpect(header().string("Content-Disposition", containsString("RUN.TRACE.csv")))
                            .andExpect(content().string(containsString("runId,seq,at,type,actionId,detail,data")))
                            .andExpect(content().string(containsString("RUN.TRACE,2,2026-06-10T10:00:01Z")));

                    mockMvc.perform(get("/actiongraph/console/runs/RUN.TRACE/trace/export.jsonl")
                                    .accept("application/x-ndjson"))
                            .andExpect(status().isOk())
                            .andExpect(content().contentTypeCompatibleWith("application/x-ndjson"))
                            .andExpect(header().string("Content-Disposition", containsString("RUN.TRACE.jsonl")))
                            .andExpect(content().string(containsString("\"runId\":\"RUN.TRACE\"")))
                            .andExpect(content().string(containsString("\"type\":\"ACTION_SUCCEEDED\"")))
                            .andExpect(content().string(containsString("\"message\":\"approved, \\\"fast\\\"\"")));

                    mockMvc.perform(get("/actiongraph/console/runs/MISSING/trace/export.csv"))
                            .andExpect(status().isNotFound())
                            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
                });
    }

    private MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static Map<String, String> orderedData(String firstKey, String firstValue, String secondKey, String secondValue) {
        Map<String, String> data = new LinkedHashMap<>();
        data.put(firstKey, firstValue);
        data.put(secondKey, secondValue);
        return data;
    }

    private static void seed(
            InMemoryConsoleRunRepository repository,
            String runId,
            String startedAt,
            TraceEventType terminalType,
            Map<String, String> terminalData,
            boolean auditComplete
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
                auditComplete ? "Trace chain is valid" : "Trace event 2 is missing hash data"
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
