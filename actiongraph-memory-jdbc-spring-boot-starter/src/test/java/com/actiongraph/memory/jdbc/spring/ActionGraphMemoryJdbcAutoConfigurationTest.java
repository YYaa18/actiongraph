package com.actiongraph.memory.jdbc.spring;

import com.actiongraph.jdbc.spring.ActionGraphJdbcAutoConfiguration;
import com.actiongraph.memory.InMemoryMemoryRepository;
import com.actiongraph.memory.MemoryContextLoader;
import com.actiongraph.memory.MemoryRepository;
import com.actiongraph.memory.spring.ActionGraphMemoryAutoConfiguration;
import com.actiongraph.memory.jdbc.JdbcMemoryRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphMemoryJdbcAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphJdbcAutoConfiguration.class,
                    ActionGraphMemoryJdbcAutoConfiguration.class
            ));

    private final ApplicationContextRunner memoryCompositionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphJdbcAutoConfiguration.class,
                    ActionGraphMemoryJdbcAutoConfiguration.class,
                    ActionGraphMemoryAutoConfiguration.class
            ));

    @Test
    void jdbcMemoryRepositoryIsDisabledByDefault() {
        contextRunner
                .withBean(DataSource.class, ActionGraphMemoryJdbcAutoConfigurationTest::h2)
                .run(context -> assertThat(context).doesNotHaveBean(MemoryRepository.class));
    }

    @Test
    void jdbcMemoryRepositoryIsCreatedWhenEnabled() {
        contextRunner
                .withBean(DataSource.class, ActionGraphMemoryJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> assertThat(context.getBean(MemoryRepository.class))
                        .isInstanceOf(JdbcMemoryRepository.class));
    }

    @Test
    void jdbcMemoryRepositoryWinsOverMemoryStarterDefault() {
        memoryCompositionRunner
                .withBean(DataSource.class, ActionGraphMemoryJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(MemoryRepository.class))
                            .isInstanceOf(JdbcMemoryRepository.class);
                    assertThat(context).hasSingleBean(MemoryContextLoader.class);
                });
    }

    @Test
    void memoryStarterStillProvidesInMemoryRepositoryWithoutJdbc() {
        memoryCompositionRunner
                .withBean(DataSource.class, ActionGraphMemoryJdbcAutoConfigurationTest::h2)
                .run(context -> assertThat(context.getBean(MemoryRepository.class))
                        .isInstanceOf(InMemoryMemoryRepository.class));
    }

    private static DataSource h2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
