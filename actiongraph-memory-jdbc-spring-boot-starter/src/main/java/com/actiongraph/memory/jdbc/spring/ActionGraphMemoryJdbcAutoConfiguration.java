package com.actiongraph.memory.jdbc.spring;

import com.actiongraph.jdbc.spring.ActionGraphJdbcProperties;
import com.actiongraph.memory.MemoryRepository;
import com.actiongraph.memory.jdbc.JdbcMemoryRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(
        beforeName = "com.actiongraph.memory.spring.ActionGraphMemoryAutoConfiguration",
        afterName = "com.actiongraph.jdbc.spring.ActionGraphJdbcAutoConfiguration"
)
@ConditionalOnClass(JdbcMemoryRepository.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        prefix = "actiongraph.persistence.jdbc",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphJdbcProperties.class)
public class ActionGraphMemoryJdbcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public MemoryRepository actionGraphJdbcMemoryRepository(
            DataSource dataSource,
            ActionGraphJdbcProperties properties
    ) {
        return new JdbcMemoryRepository(dataSource, properties.getTables().getMemory());
    }
}
