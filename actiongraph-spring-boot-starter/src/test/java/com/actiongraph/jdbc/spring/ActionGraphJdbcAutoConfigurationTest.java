package com.actiongraph.jdbc.spring;

import com.actiongraph.persistence.jdbc.BlackboardTypeRegistry;
import com.actiongraph.persistence.jdbc.JdbcSuspendedRunRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import com.actiongraph.runtime.Executor;
import com.actiongraph.runtime.SuspendedRunRepository;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import com.actiongraph.trace.InMemoryTraceRepository;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;
import java.time.Instant;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphJdbcAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphJdbcAutoConfiguration.class));

    private final ApplicationContextRunner runtimeCompositionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphJdbcAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class
            ));

    @Test
    void jdbcRepositoriesAreDisabledByDefault() {
        contextRunner
                .withBean(DataSource.class, ActionGraphJdbcAutoConfigurationTest::h2)
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TraceRepository.class);
                    assertThat(context).doesNotHaveBean(SuspendedRunRepository.class);
                    assertThat(context).doesNotHaveBean(JdbcTraceRunRepository.class);
                });
    }

    @Test
    void jdbcRepositoriesAreNotCreatedWithoutDataSource() {
        contextRunner
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(TraceRepository.class);
                    assertThat(context).doesNotHaveBean(SuspendedRunRepository.class);
                    assertThat(context).doesNotHaveBean(JdbcTraceRunRepository.class);
                });
    }

    @Test
    void enabledJdbcRepositoriesAreAutoConfigured() {
        contextRunner
                .withBean(DataSource.class, ActionGraphJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(TraceRepository.class))
                            .isInstanceOf(JdbcTraceRepository.class);
                    assertThat(context.getBean(SuspendedRunRepository.class))
                            .isInstanceOf(JdbcSuspendedRunRepository.class);
                    assertThat(context).hasSingleBean(JdbcTraceRunRepository.class);
                    assertThat(context.getBean(BlackboardTypeRegistry.class).allowAllTypes()).isTrue();
                });
    }

    @Test
    void customTablesAndBlackboardAllowlistAreApplied() {
        contextRunner
                .withBean(DataSource.class, ActionGraphJdbcAutoConfigurationTest::h2)
                .withPropertyValues(
                        "actiongraph.persistence.jdbc.enabled=true",
                        "actiongraph.persistence.jdbc.tables.trace-event=ag_trace",
                        "actiongraph.persistence.jdbc.tables.suspended-run=ag_suspended",
                        "actiongraph.persistence.jdbc.blackboard.allowed-classes=java.lang.String",
                        "actiongraph.persistence.jdbc.blackboard.allowed-packages=com.example.domain"
                )
                .run(context -> {
                    TraceRepository traceRepository = context.getBean(TraceRepository.class);
                    traceRepository.append(new TraceEvent(
                            "RUN-1",
                            1,
                            Instant.parse("2026-06-10T10:00:00Z"),
                            TraceEventType.RUN_STARTED,
                            null,
                            "started",
                            Map.of()
                    ));

                    assertThat(context.getBean(JdbcTraceRunRepository.class).findRun("RUN-1"))
                            .get()
                            .satisfies(summary -> assertThat(summary.traceEventCount()).isEqualTo(1));

                    BlackboardTypeRegistry registry = context.getBean(BlackboardTypeRegistry.class);
                    assertThat(registry.allowAllTypes()).isFalse();
                    assertThat(registry.allows("java.lang.String")).isTrue();
                    assertThat(registry.allows("com.example.domain.ClaimSnapshot")).isTrue();
                    assertThat(registry.allows("com.other.Untrusted")).isFalse();
                });
    }

    @Test
    void applicationBeansCanOverrideJdbcRepositoriesAndRegistry() {
        InMemoryTraceRepository customTraceRepository = new InMemoryTraceRepository();
        BlackboardTypeRegistry customRegistry = BlackboardTypeRegistry.builder()
                .allowPackage("com.example.safe")
                .build();

        contextRunner
                .withBean(DataSource.class, ActionGraphJdbcAutoConfigurationTest::h2)
                .withBean(TraceRepository.class, () -> customTraceRepository)
                .withBean(BlackboardTypeRegistry.class, () -> customRegistry)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(TraceRepository.class)).isSameAs(customTraceRepository);
                    assertThat(context.getBean(BlackboardTypeRegistry.class)).isSameAs(customRegistry);
                    assertThat(context.getBean(SuspendedRunRepository.class))
                            .isInstanceOf(JdbcSuspendedRunRepository.class);
                });
    }

    @Test
    void jdbcRepositoriesComposeBeforeRuntimeStarterDefaults() {
        runtimeCompositionRunner
                .withBean(DataSource.class, ActionGraphJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(TraceRepository.class))
                            .isInstanceOf(JdbcTraceRepository.class);
                    assertThat(context.getBean(SuspendedRunRepository.class))
                            .isInstanceOf(JdbcSuspendedRunRepository.class);
                    assertThat(context).hasSingleBean(Executor.class);
                });
    }

    private static DataSource h2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
