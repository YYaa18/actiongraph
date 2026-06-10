package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(after = ActionGraphConsoleServiceAutoConfiguration.class)
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
public class ActionGraphConsoleApiAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean(name = "actionGraphConsoleApiController")
    @ConditionalOnBean(ActionGraphConsoleService.class)
    public ActionGraphConsoleApiController actionGraphConsoleApiController(
            ActionGraphConsoleService consoleService,
            ActionGraphConsoleProperties properties
    ) {
        return new ActionGraphConsoleApiController(consoleService, properties);
    }
}
