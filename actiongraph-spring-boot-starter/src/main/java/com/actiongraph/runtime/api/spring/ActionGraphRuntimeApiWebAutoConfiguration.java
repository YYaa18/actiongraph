package com.actiongraph.runtime.api.spring;

import com.actiongraph.ActionGraph;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.runtime.api.ActionGraphRuntimeApiService;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifiers;
import org.springframework.beans.factory.ObjectProvider;
import com.actiongraph.runtime.api.ActionGraphRuntimeOperations;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "com.actiongraph.spring.ActionGraphAutoConfiguration")
@ConditionalOnClass(name = {
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        prefix = "actiongraph.runtime.api",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphRuntimeApiProperties.class)
public class ActionGraphRuntimeApiWebAutoConfiguration {
    @Bean
    @ConditionalOnBean({ActionGraph.class, GoalInterpreter.class})
    @ConditionalOnMissingBean(ActionGraphRuntimeOperations.class)
    public ActionGraphRuntimeApiService actionGraphRuntimeApiService(
            ActionGraph actionGraph,
            GoalInterpreter interpreter
    ) {
        return new ActionGraphRuntimeApiService(actionGraph, interpreter);
    }

    @Bean
    @ConditionalOnBean(ActionGraphRuntimeOperations.class)
    @ConditionalOnMissingBean(name = "actionGraphRuntimeApiController")
    public ActionGraphRuntimeApiController actionGraphRuntimeApiController(
            ActionGraphRuntimeOperations apiService,
            ActionGraphRuntimeApiProperties properties,
            ObjectProvider<ActionGraphEndpointAccessVerifier> accessVerifier
    ) {
        return new ActionGraphRuntimeApiController(
                apiService,
                properties,
                ActionGraphEndpointAccessVerifiers.getOrSharedSecretDefault(accessVerifier)
        );
    }
}
