package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import com.actiongraph.console.ConsoleOptions;
import com.actiongraph.console.ConsoleRunRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(name = {
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "actiongraph.console",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphConsoleProperties.class)
public class ActionGraphConsoleWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(ConsoleRunRepository.class)
    public ActionGraphConsoleService actionGraphConsoleService(
            ConsoleRunRepository runRepository,
            ActionGraphConsoleProperties properties
    ) {
        return new ActionGraphConsoleService(
                runRepository,
                new ConsoleOptions(
                        properties.getTokenHeader(),
                        properties.getDefaultLimit(),
                        properties.getMaxLimit()
                )
        );
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphConsoleController")
    @ConditionalOnBean(ActionGraphConsoleService.class)
    public ActionGraphConsoleController actionGraphConsoleController(
            ActionGraphConsoleService consoleService,
            ActionGraphConsoleProperties properties
    ) {
        return new ActionGraphConsoleController(consoleService, properties);
    }
}
