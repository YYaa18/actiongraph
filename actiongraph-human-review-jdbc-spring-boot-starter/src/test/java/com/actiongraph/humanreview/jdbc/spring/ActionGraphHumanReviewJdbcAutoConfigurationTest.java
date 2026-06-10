package com.actiongraph.humanreview.jdbc.spring;

import com.actiongraph.humanreview.spring.ActionGraphHumanReviewAutoConfiguration;
import com.actiongraph.jdbc.spring.ActionGraphJdbcAutoConfiguration;
import com.actiongraph.humanreview.jdbc.JdbcHumanReviewRepository;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.policy.RepositoryBackedHumanReviewPolicy;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import javax.sql.DataSource;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphHumanReviewJdbcAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphJdbcAutoConfiguration.class,
                    ActionGraphHumanReviewJdbcAutoConfiguration.class
            ));

    private final ApplicationContextRunner humanReviewCompositionRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphJdbcAutoConfiguration.class,
                    ActionGraphHumanReviewJdbcAutoConfiguration.class,
                    ActionGraphHumanReviewAutoConfiguration.class
            ));

    @Test
    void jdbcHumanReviewRepositoryIsDisabledByDefault() {
        contextRunner
                .withBean(DataSource.class, ActionGraphHumanReviewJdbcAutoConfigurationTest::h2)
                .run(context -> assertThat(context).doesNotHaveBean(HumanReviewRepository.class));
    }

    @Test
    void jdbcHumanReviewRepositoryIsCreatedWhenEnabled() {
        contextRunner
                .withBean(DataSource.class, ActionGraphHumanReviewJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> assertThat(context.getBean(HumanReviewRepository.class))
                        .isInstanceOf(JdbcHumanReviewRepository.class));
    }

    @Test
    void jdbcHumanReviewRepositoryWinsOverHumanReviewStarterDefault() {
        humanReviewCompositionRunner
                .withBean(DataSource.class, ActionGraphHumanReviewJdbcAutoConfigurationTest::h2)
                .withPropertyValues("actiongraph.persistence.jdbc.enabled=true")
                .run(context -> {
                    assertThat(context.getBean(HumanReviewRepository.class))
                            .isInstanceOf(JdbcHumanReviewRepository.class);
                    assertThat(context.getBean(HumanReviewPolicy.class))
                            .isInstanceOf(RepositoryBackedHumanReviewPolicy.class);
                });
    }

    @Test
    void humanReviewStarterStillProvidesInMemoryRepositoryWithoutJdbc() {
        humanReviewCompositionRunner
                .withBean(DataSource.class, ActionGraphHumanReviewJdbcAutoConfigurationTest::h2)
                .run(context -> assertThat(context.getBean(HumanReviewRepository.class))
                        .isInstanceOf(InMemoryHumanReviewRepository.class));
    }

    private static DataSource h2() {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + java.util.UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        return dataSource;
    }
}
