package com.actiongraph.console.jdbc.spring;

import com.actiongraph.console.ConsoleRunRepository;
import com.actiongraph.console.jdbc.JdbcConsoleRunRepository;
import com.actiongraph.console.spring.ActionGraphConsoleApiAutoConfiguration;
import com.actiongraph.console.spring.ActionGraphConsoleApiController;
import com.actiongraph.console.spring.ActionGraphConsoleServiceAutoConfiguration;
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
import org.springframework.context.annotation.Bean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphConsoleJdbcAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphConsoleJdbcAutoConfiguration.class
            ));

    @Test
    void jdbcConsoleRepositoryIsDisabledByDefault() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleJdbcAutoConfigurationTest::h2)
                .run(context -> assertThat(context).doesNotHaveBean(ConsoleRunRepository.class));
    }

    @Test
    void createsJdbcConsoleRepositoryWhenEnabled() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ConsoleRunRepository.class);
                    assertThat(context.getBean(ConsoleRunRepository.class))
                            .isInstanceOf(JdbcConsoleRunRepository.class);
                });
    }

    @Test
    void backsOffToApplicationConsoleRepository() {
        ConsoleRunRepository customRepository = new JdbcConsoleRunRepository(h2());
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleJdbcAutoConfigurationTest::h2)
                .withBean(ConsoleRunRepository.class, () -> customRepository)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> assertThat(context.getBean(ConsoleRunRepository.class))
                        .isSameAs(customRepository));
    }

    @Test
    void reusesExistingJdbcTraceRunRepository() {
        contextRunner
                .withBean(DataSource.class, ActionGraphConsoleJdbcAutoConfigurationTest::h2)
                .withUserConfiguration(CustomTraceRunRepositoryConfiguration.class)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    seed(context.getBean(DataSource.class), "custom_trace", "RUN-CUSTOM",
                            "2026-06-10T10:00:00Z", TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

                    ConsoleRunRepository repository = context.getBean(ConsoleRunRepository.class);

                    assertThat(repository.findRun("RUN-CUSTOM")).isPresent();
                    assertThat(repository.findRuns(new com.actiongraph.console.ConsoleRunQuery(
                            10, 0, "COMPLETED", true
                    )).runs()).singleElement().satisfies(run ->
                            assertThat(run.runId()).isEqualTo("RUN-CUSTOM"));
                });
    }

    @Test
    void composesWithConsoleApiStarter() {
        new WebApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(
                        JacksonAutoConfiguration.class,
                        HttpMessageConvertersAutoConfiguration.class,
                        WebMvcAutoConfiguration.class,
                        ActionGraphConsoleJdbcAutoConfiguration.class,
                        ActionGraphConsoleServiceAutoConfiguration.class,
                        ActionGraphConsoleApiAutoConfiguration.class
                ))
                .withBean(DataSource.class, ActionGraphConsoleJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ConsoleRunRepository.class);
                    assertThat(context).hasSingleBean(ActionGraphConsoleApiController.class);
                    seed(context.getBean(DataSource.class), "RUN-COMPLETED", "2026-06-10T10:00:00Z",
                            TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));

                    MockMvcBuilders.webAppContextSetup((WebApplicationContext) context.getSourceApplicationContext())
                            .build()
                            .perform(get("/actiongraph/console/runs")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.total").value(1))
                            .andExpect(jsonPath("$.runs[0].runId").value("RUN-COMPLETED"));
                });
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
        seed(dataSource, JdbcTraceRepository.DEFAULT_TABLE, runId, startedAt, terminalType, terminalData);
    }

    private static void seed(
            DataSource dataSource,
            String table,
            String runId,
            String startedAt,
            TraceEventType terminalType,
            Map<String, String> terminalData
    ) {
        JdbcTraceRepository repository = new JdbcTraceRepository(dataSource, table);
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

    static final class CustomTraceRunRepositoryConfiguration {
        @Bean
        JdbcTraceRunRepository jdbcTraceRunRepository(DataSource dataSource) {
            return new JdbcTraceRunRepository(dataSource, "custom_trace");
        }
    }
}
