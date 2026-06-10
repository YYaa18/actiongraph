package com.actiongraph.spring;

import com.actiongraph.persistence.jdbc.JdbcTraceRunRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

import javax.sql.DataSource;

@AutoConfiguration(
        after = ActionGraphAutoConfiguration.class,
        afterName = "org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration"
)
@ConditionalOnClass(name = {
        "com.actiongraph.persistence.jdbc.JdbcTraceRunRepository",
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(DataSource.class)
@ConditionalOnProperty(
        prefix = "actiongraph.console",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphProperties.class)
public class ActionGraphConsoleWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public JdbcTraceRunRepository actionGraphTraceRunRepository(DataSource dataSource) {
        return new JdbcTraceRunRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphConsoleController")
    public ActionGraphConsoleController actionGraphConsoleController(
            JdbcTraceRunRepository traceRunRepository,
            ActionGraphProperties properties
    ) {
        return new ActionGraphConsoleController(traceRunRepository, properties.getConsole());
    }
}
