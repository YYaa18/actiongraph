package com.actiongraph.console.spring;

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
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphConsoleCompatibilityStarterTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphConsoleServiceAutoConfiguration.class,
                    ActionGraphConsoleApiAutoConfiguration.class,
                    ActionGraphConsoleUiAutoConfiguration.class
            ));

    @Test
    void compatibilityStarterExposesConsolePageAndJsonApi() {
        contextRunner
                .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsoleApiController.class);
                    assertThat(context).hasSingleBean(ActionGraphConsolePageController.class);

                    InMemoryConsoleRunRepository repository = context.getBean(InMemoryConsoleRunRepository.class);
                    seed(repository, "RUN-COMPLETED", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

                    MockMvc mockMvc = mockMvc(context);
                    mockMvc.perform(get("/actiongraph/console")
                                    .accept(MediaType.TEXT_HTML))
                            .andExpect(status().isOk())
                            .andExpect(content().string(containsString("ActionGraph Console")));

                    mockMvc.perform(get("/actiongraph/console/runs")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.total").value(1))
                            .andExpect(jsonPath("$.runs[0].runId").value("RUN-COMPLETED"));
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
