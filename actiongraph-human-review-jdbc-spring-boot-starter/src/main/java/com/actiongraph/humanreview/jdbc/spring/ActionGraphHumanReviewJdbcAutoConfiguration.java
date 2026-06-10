package com.actiongraph.humanreview.jdbc.spring;

import com.actiongraph.jdbc.spring.ActionGraphJdbcProperties;
import com.actiongraph.humanreview.jdbc.JdbcHumanReviewRepository;
import com.actiongraph.policy.HumanReviewRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(
        beforeName = "com.actiongraph.humanreview.spring.ActionGraphHumanReviewAutoConfiguration",
        afterName = "com.actiongraph.jdbc.spring.ActionGraphJdbcAutoConfiguration"
)
@ConditionalOnClass(JdbcHumanReviewRepository.class)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        prefix = "actiongraph.persistence.jdbc",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphJdbcProperties.class)
public class ActionGraphHumanReviewJdbcAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public HumanReviewRepository actionGraphJdbcHumanReviewRepository(
            DataSource dataSource,
            ActionGraphJdbcProperties properties
    ) {
        return new JdbcHumanReviewRepository(dataSource, properties.getTables().getHumanReview());
    }
}
