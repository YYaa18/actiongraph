package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleOptions;
import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceHasher;
import org.h2.jdbcx.JdbcDataSource;
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

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphConsoleWebAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphConsoleWebAutoConfiguration.class
            ));

    @Test
    void consoleEndpointIsDisabledByDefault() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleWebAutoConfigurationTest::h2)
                .run(context -> assertThat(context).doesNotHaveBean(ActionGraphConsoleController.class));
    }

    @Test
    void consoleEndpointIsNotCreatedWithoutDataSource() {
        contextRunner
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> assertThat(context).doesNotHaveBean(ActionGraphConsoleController.class));
    }

    @Test
    void consoleServesReadOnlyPageWithInjectedConfiguration() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleWebAutoConfigurationTest::h2)
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.path=/internal/actiongraph-console",
                        "actiongraph.console.token-header=X-Internal-Console-Token",
                        "actiongraph.console.default-limit=25",
                        "actiongraph.console.max-limit=75"
                )
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsoleService.class);
                    mockMvc(context).perform(get("/internal/actiongraph-console")
                                    .accept(MediaType.TEXT_HTML))
                            .andExpect(status().isOk())
                            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                            .andExpect(content().string(containsString("ActionGraph Console")))
                            .andExpect(content().string(containsString("Run monitoring and trace audit")))
                            .andExpect(content().string(containsString("tokenHeader: 'X-Internal-Console-Token'")))
                            .andExpect(content().string(containsString("defaultLimit: 25")))
                            .andExpect(content().string(containsString("maxLimit: 75")))
                            .andExpect(content().string(containsString("\"/trace\"")));
                });
    }

    @Test
    void applicationCanOverrideConsoleService() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleWebAutoConfigurationTest::h2)
                .withBean(ActionGraphConsoleService.class, () -> new ActionGraphConsoleService(
                        new JdbcTraceRunRepository(h2()),
                        new ConsoleOptions("X-Custom-Console-Token", 3, 9)
                ))
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsoleService.class);
                    mockMvc(context).perform(get("/actiongraph/console")
                                    .accept(MediaType.TEXT_HTML))
                            .andExpect(status().isOk())
                            .andExpect(content().string(containsString("tokenHeader: 'X-Custom-Console-Token'")))
                            .andExpect(content().string(containsString("defaultLimit: 3")))
                            .andExpect(content().string(containsString("maxLimit: 9")));
                });
    }

    @Test
    void consoleListsRecentRunsAndRequiresConfiguredToken() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleWebAutoConfigurationTest::h2)
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.shared-secret=console-secret"
                )
                .run(context -> {
                    seed(context.getBean(DataSource.class), "RUN-COMPLETED", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
                    seed(context.getBean(DataSource.class), "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
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
    void consoleFiltersRunsAndReturnsTraceEvents() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleWebAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    seed(context.getBean(DataSource.class), "RUN-OLDER", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
                    seed(context.getBean(DataSource.class), "RUN-SUSPENDED", "2026-06-10T10:05:00Z",
                            TraceEventType.RUN_SUSPENDED, Map.of("status", "SUSPENDED_PENDING_REVIEW"));
                    seed(context.getBean(DataSource.class), "RUN-NEWER", "2026-06-10T10:10:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
                    seedLegacy(context.getBean(DataSource.class), "RUN-LEGACY", "2026-06-10T10:15:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

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
    void consoleReturnsRunDetailsAndNotFoundErrors() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleWebAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    seed(context.getBean(DataSource.class), "RUN-COMPLETED", "2026-06-10T10:00:00Z",
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
    void consoleSupportsCustomPathHeaderAndLimitGuard() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleWebAutoConfigurationTest::h2)
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.path=/internal/actiongraph-console",
                        "actiongraph.console.token-header=X-Internal-Console-Token",
                        "actiongraph.console.shared-secret=console-secret",
                        "actiongraph.console.default-limit=1",
                        "actiongraph.console.max-limit=1"
                )
                .run(context -> {
                    seed(context.getBean(DataSource.class), "RUN-1", "2026-06-10T10:00:00Z",
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
