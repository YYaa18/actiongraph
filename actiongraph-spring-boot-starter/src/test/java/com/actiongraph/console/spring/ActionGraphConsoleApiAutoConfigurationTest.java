package com.actiongraph.console.spring;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphConsoleApiAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphConsoleServiceAutoConfiguration.class,
                    ActionGraphConsoleApiAutoConfiguration.class
            ));

    @Test
    void consoleApiEndpointIsDisabledByDefault() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .run(context -> assertThat(context).doesNotHaveBean(ActionGraphConsoleApiController.class));
    }

    @Test
    void consoleApiEndpointIsNotCreatedWithoutRunRepositoryOrService() {
        contextRunner
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleApiController.class);
                });
    }

    @Test
    void consoleApiAutoConfigurationDoesNotCreatePageController() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsoleApiController.class);
                    assertThat(context).doesNotHaveBean(ActionGraphConsolePageController.class);
                });
    }

    @Test
    void applicationCanOverrideConsoleServiceWithoutDataSource() {
        contextRunner
                .withBean(ActionGraphConsoleService.class, () -> new ActionGraphConsoleService(
                        new InMemoryConsoleRunRepository(),
                        new ConsoleOptions("X-Custom-Console-Token", 3, 9)
                ))
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsoleService.class);
                    assertThat(context).hasSingleBean(ActionGraphConsoleApiController.class);
                    mockMvc(context).perform(get("/actiongraph/console/runs")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.limit").value(3))
                            .andExpect(jsonPath("$.count").value(0));
                });
    }

    @Test
    void consoleApiListsRecentRunsAndRequiresConfiguredToken() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.shared-secret=console-secret"
                )
                .run(context -> {
                    InMemoryConsoleRunRepository repository = context.getBean(InMemoryConsoleRunRepository.class);
                    seed(repository, "RUN-COMPLETED", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
                    seed(repository, "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
                            TraceEventType.RUN_SUSPENDED, Map.of("status", "SUSPENDED_PENDING_REVIEW"));

                    MockMvc mockMvc = mockMvc(context);
                    mockMvc.perform(get("/actiongraph/console/runs")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    mockMvc.perform(get("/actiongraph/console/runs")
                                    .queryParam("limit", "1")
                                    .header("X-ActionGraph-Console-Token", "console-secret")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.limit").value(1))
                            .andExpect(jsonPath("$.offset").value(0))
                            .andExpect(jsonPath("$.total").value(2))
                            .andExpect(jsonPath("$.count").value(1))
                            .andExpect(jsonPath("$.hasMore").value(true))
                            .andExpect(jsonPath("$.runs[0].runId").value("RUN-SUSPENDED"))
                            .andExpect(jsonPath("$.runs[0].status").value("SUSPENDED_PENDING_REVIEW"))
                            .andExpect(jsonPath("$.runs[0].traceEventCount").value(2))
                            .andExpect(jsonPath("$.runs[0].auditComplete").value(true))
                            .andExpect(jsonPath("$.runs[0].firstBrokenSeq").value(0))
                            .andExpect(jsonPath("$.runs[0].auditMessage").value("Trace chain is valid"));
                });
    }

    @Test
    void consoleApiFiltersRunsAndReturnsTraceEvents() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    InMemoryConsoleRunRepository repository = context.getBean(InMemoryConsoleRunRepository.class);
                    seed(repository, "RUN-OLDER", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
                    seed(repository, "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
                            TraceEventType.RUN_SUSPENDED, Map.of("status", "SUSPENDED_PENDING_REVIEW"));
                    seed(repository, "RUN-NEWER", "2026-06-10T10:10:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
                    seed(repository, "RUN-LEGACY", "2026-06-10T10:15:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"), false);

                    MockMvc mockMvc = mockMvc(context);
                    mockMvc.perform(get("/actiongraph/console/runs")
                                    .queryParam("status", "COMPLETED")
                                    .queryParam("auditComplete", "true")
                                    .queryParam("limit", "1")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.limit").value(1))
                            .andExpect(jsonPath("$.offset").value(0))
                            .andExpect(jsonPath("$.total").value(2))
                            .andExpect(jsonPath("$.count").value(1))
                            .andExpect(jsonPath("$.hasMore").value(true))
                            .andExpect(jsonPath("$.status").value("COMPLETED"))
                            .andExpect(jsonPath("$.auditComplete").value(true))
                            .andExpect(jsonPath("$.runs[0].runId").value("RUN-NEWER"));

                    mockMvc.perform(get("/actiongraph/console/runs")
                                    .queryParam("auditComplete", "false")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.total").value(1))
                            .andExpect(jsonPath("$.runs[0].runId").value("RUN-LEGACY"))
                            .andExpect(jsonPath("$.runs[0].auditComplete").value(false));

                    mockMvc.perform(get("/actiongraph/console/runs/RUN-NEWER/trace")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.runId").value("RUN-NEWER"))
                            .andExpect(jsonPath("$.count").value(2))
                            .andExpect(jsonPath("$.events[0].seq").value(1))
                            .andExpect(jsonPath("$.events[0].type").value("RUN_STARTED"))
                            .andExpect(jsonPath("$.events[1].type").value("RUN_ENDED"))
                            .andExpect(jsonPath("$.events[1].data.status").value("COMPLETED"))
                            .andExpect(jsonPath("$.events[1].hash").isNotEmpty());

                    mockMvc.perform(get("/actiongraph/console/runs/MISSING/trace")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound())
                            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
                });
    }

    @Test
    void consoleApiReturnsRunDetailsAndNotFoundErrors() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    InMemoryConsoleRunRepository repository = context.getBean(InMemoryConsoleRunRepository.class);
                    seed(repository, "RUN-COMPLETED", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

                    MockMvc mockMvc = mockMvc(context);
                    mockMvc.perform(get("/actiongraph/console/runs/RUN-COMPLETED")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.runId").value("RUN-COMPLETED"))
                            .andExpect(jsonPath("$.firstEventAt").value("2026-06-10T10:00:00Z"))
                            .andExpect(jsonPath("$.lastEventAt").value("2026-06-10T10:00:01Z"))
                            .andExpect(jsonPath("$.status").value("COMPLETED"))
                            .andExpect(jsonPath("$.auditComplete").value(true));

                    mockMvc.perform(get("/actiongraph/console/runs/MISSING")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound())
                            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
                });
    }

    @Test
    void consoleApiSupportsCustomPathHeaderAndLimitGuard() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.path=/internal/actiongraph-console",
                        "actiongraph.console.token-header=X-Internal-Console-Token",
                        "actiongraph.console.shared-secret=console-secret",
                        "actiongraph.console.default-limit=1",
                        "actiongraph.console.max-limit=1"
                )
                .run(context -> {
                    InMemoryConsoleRunRepository repository = context.getBean(InMemoryConsoleRunRepository.class);
                    seed(repository, "RUN-1", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

                    MockMvc mockMvc = mockMvc(context);
                    mockMvc.perform(get("/internal/actiongraph-console/runs")
                                    .header("X-Internal-Console-Token", "console-secret")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.limit").value(1))
                            .andExpect(jsonPath("$.count").value(1));

                    mockMvc.perform(get("/internal/actiongraph-console/runs")
                                    .queryParam("limit", "2")
                                    .header("X-Internal-Console-Token", "console-secret")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isBadRequest())
                            .andExpect(jsonPath("$.error").value("BAD_REQUEST"))
                            .andExpect(jsonPath("$.message").value("limit must not exceed 1"));
                });
    }

    private MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static void seed(
            InMemoryConsoleRunRepository repository,
            String runId,
            String startedAt,
            TraceEventType terminalType,
            Map<String, String> terminalData
    ) {
        seed(repository, runId, startedAt, terminalType, terminalData, true);
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
                TraceEventType.RUN_STARTED, Map.of());
        TraceEvent ended = auditComplete
                ? hashed(runId, 2, started.hash(), Instant.parse(startedAt).plusSeconds(1), terminalType, terminalData)
                : new TraceEvent(runId, 2, Instant.parse(startedAt).plusSeconds(1), terminalType, null,
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
            Map<String, String> data
    ) {
        String hash = TraceHasher.hash(runId, seq, at, type, null, type.name(), data, prevHash);
        return new TraceEvent(runId, seq, at, type, null, type.name(), data, prevHash, hash);
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
