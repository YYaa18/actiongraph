package com.actiongraph.events.spring;

import com.actiongraph.events.ExternalEventGateway;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import com.actiongraph.spring.ActionGraphProperties;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.web.bind.annotation.RestController;

@AutoConfiguration(after = ActionGraphAutoConfiguration.class)
@ConditionalOnClass(RestController.class)
@ConditionalOnProperty(
        prefix = "actiongraph.events.callback-endpoint",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphProperties.class)
public class ActionGraphEventCallbackWebAutoConfiguration {
    @Bean
    @ConditionalOnBean(ExternalEventGateway.class)
    @ConditionalOnMissingBean
    public ActionGraphEventCallbackController actionGraphEventCallbackController(
            ExternalEventGateway gateway,
            ActionGraphProperties properties
    ) {
        return new ActionGraphEventCallbackController(gateway, properties);
    }
}
