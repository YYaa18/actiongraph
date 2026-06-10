package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleOptions;
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
@EnableConfigurationProperties(ActionGraphConsoleProperties.class)
public class ActionGraphConsoleWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public JdbcTraceRunRepository actionGraphTraceRunRepository(DataSource dataSource) {
        return new JdbcTraceRunRepository(dataSource);
    }

    @Bean
    @ConditionalOnMissingBean
    public ActionGraphConsoleService actionGraphConsoleService(
            JdbcTraceRunRepository traceRunRepository,
            ActionGraphConsoleProperties properties
    ) {
        return new ActionGraphConsoleService(
                traceRunRepository,
                new ConsoleOptions(
                        properties.getTokenHeader(),
                        properties.getDefaultLimit(),
                        properties.getMaxLimit()
                )
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphConsoleController")
    public ActionGraphConsoleController actionGraphConsoleController(
            ActionGraphConsoleService consoleService,
            ActionGraphConsoleProperties properties
    ) {
        return new ActionGraphConsoleController(consoleService, properties);
    }
}
