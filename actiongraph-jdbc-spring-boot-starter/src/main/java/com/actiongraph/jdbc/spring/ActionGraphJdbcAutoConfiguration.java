package com.actiongraph.jdbc.spring;

import com.actiongraph.memory.MemoryRepository;
import com.actiongraph.persistence.jdbc.BlackboardTypeRegistry;
import com.actiongraph.persistence.jdbc.JdbcHumanReviewRepository;
import com.actiongraph.persistence.jdbc.JdbcMemoryRepository;
import com.actiongraph.persistence.jdbc.JdbcSuspendedRunRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.runtime.SuspendedRunRepository;
import com.actiongraph.trace.TraceRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(
        beforeName = {
                "com.actiongraph.spring.ActionGraphAutoConfiguration",
                "com.actiongraph.console.spring.ActionGraphConsoleWebAutoConfiguration"
        },
        afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
)
@ConditionalOnClass(name = {
        "com.actiongraph.persistence.jdbc.JdbcTraceRepository",
        "javax.sql.DataSource"
})
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        prefix = "actiongraph.persistence.jdbc",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphJdbcProperties.class)
public class ActionGraphJdbcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public BlackboardTypeRegistry actionGraphJdbcBlackboardTypeRegistry(ActionGraphJdbcProperties properties) {
        return properties.toBlackboardTypeRegistry();
    }

    @Bean
    @ConditionalOnMissingBean
    public TraceRepository actionGraphJdbcTraceRepository(
            DataSource dataSource,
            ActionGraphJdbcProperties properties
    ) {
        return new JdbcTraceRepository(dataSource, properties.getTables().getTraceEvent());
    }

    @Bean
    @ConditionalOnMissingBean
    public SuspendedRunRepository actionGraphJdbcSuspendedRunRepository(
            DataSource dataSource,
            BlackboardTypeRegistry blackboardTypeRegistry,
            ActionGraphJdbcProperties properties
    ) {
        return new JdbcSuspendedRunRepository(
                dataSource,
                properties.getTables().getSuspendedRun(),
                properties.getSuspendedRunClaimTimeout(),
                blackboardTypeRegistry
        );
    }

    @Bean
    @ConditionalOnMissingBean
    public HumanReviewRepository actionGraphJdbcHumanReviewRepository(
            DataSource dataSource,
            ActionGraphJdbcProperties properties
    ) {
        return new JdbcHumanReviewRepository(dataSource, properties.getTables().getHumanReview());
    }

    @Bean
    @ConditionalOnMissingBean
    public MemoryRepository actionGraphJdbcMemoryRepository(
            DataSource dataSource,
            ActionGraphJdbcProperties properties
    ) {
        return new JdbcMemoryRepository(dataSource, properties.getTables().getMemory());
    }

    @Bean
    @ConditionalOnMissingBean
    public JdbcTraceRunRepository actionGraphJdbcTraceRunRepository(
            DataSource dataSource,
            ActionGraphJdbcProperties properties
    ) {
        return new JdbcTraceRunRepository(dataSource, properties.getTables().getTraceEvent());
    }
}
