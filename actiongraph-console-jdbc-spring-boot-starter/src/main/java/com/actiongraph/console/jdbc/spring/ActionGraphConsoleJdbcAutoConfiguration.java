package com.actiongraph.console.jdbc.spring;

import com.actiongraph.console.ConsoleRunRepository;
import com.actiongraph.console.jdbc.JdbcConsoleRunRepository;
import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(
        beforeName = "com.actiongraph.console.spring.ActionGraphConsoleWebAutoConfiguration",
        afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
)
@ConditionalOnClass(JdbcConsoleRunRepository.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        prefix = "actiongraph.console",
        name = "enabled",
        havingValue = "true"
)
public class ActionGraphConsoleJdbcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ConsoleRunRepository actionGraphConsoleRunRepository(
            DataSource dataSource,
            ObjectProvider<JdbcTraceRunRepository> traceRunRepository
    ) {
        return new JdbcConsoleRunRepository(traceRunRepository.getIfAvailable(
                () -> new JdbcTraceRunRepository(dataSource)
        ));
    }
}
