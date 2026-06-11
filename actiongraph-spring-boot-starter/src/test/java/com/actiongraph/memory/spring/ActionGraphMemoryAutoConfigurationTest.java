package com.actiongraph.memory.spring;

import com.actiongraph.jdbc.spring.ActionGraphJdbcAutoConfiguration;
import com.actiongraph.memory.jdbc.spring.ActionGraphMemoryJdbcAutoConfiguration;
import com.actiongraph.memory.InMemoryMemoryRepository;
import com.actiongraph.memory.MemoryContextLoader;
import com.actiongraph.memory.MemoryRepository;
import com.actiongraph.memory.jdbc.JdbcMemoryRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphMemoryAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphMemoryAutoConfiguration.class));

    private final ApplicationContextRunner jdbcCompositionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphJdbcAutoConfiguration.class,
                    ActionGraphMemoryJdbcAutoConfiguration.class,
                    ActionGraphMemoryAutoConfiguration.class
            ));

    @Test
    void createsInMemoryMemoryRepositoryAndContextLoader() {
        contextRunner.run(context -> {
            assertThat(context.getBean(MemoryRepository.class))
                    .isInstanceOf(InMemoryMemoryRepository.class);
            assertThat(context).hasSingleBean(MemoryContextLoader.class);
        });
    }

    @Test
    void backsOffWhenApplicationProvidesMemoryRepositoryAndContextLoader() {
        InMemoryMemoryRepository repository = new InMemoryMemoryRepository();
        MemoryContextLoader loader = new MemoryContextLoader(repository);

        contextRunner
                .withBean(MemoryRepository.class, () -> repository)
                .withBean(MemoryContextLoader.class, () -> loader)
                .run(context -> {
                    assertThat(context.getBean(MemoryRepository.class)).isSameAs(repository);
                    assertThat(context.getBean(MemoryContextLoader.class)).isSameAs(loader);
                });
    }

    @Test
    void jdbcMemoryRepositoryWinsWhenJdbcStarterIsEnabled() {
        jdbcCompositionRunner
                .withBean(DataSource.class, ActionGraphMemoryAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(MemoryRepository.class))
                            .isInstanceOf(JdbcMemoryRepository.class);
                    assertThat(context).hasSingleBean(MemoryContextLoader.class);
                });
    }

    private static DataSource h2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
