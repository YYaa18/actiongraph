package com.actiongraph.runtime.api.spring;

import com.actiongraph.action.ActionRegistry;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.api.ActionGraphRuntimeApiService;
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
@ConditionalOnBean({
        GoalInterpreter.class,
        GoalBlackboardSeederRegistry.class,
        GoapExecutor.class,
        ActionRegistry.class
})
@ConditionalOnProperty(
        prefix = "actiongraph.runtime.api",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphRuntimeApiProperties.class)
public class ActionGraphRuntimeApiWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ActionGraphRuntimeApiService actionGraphRuntimeApiService(
            GoalInterpreter interpreter,
            GoalBlackboardSeederRegistry seeders,
            GoapExecutor executor,
            ActionRegistry registry
    ) {
        return new ActionGraphRuntimeApiService(interpreter, seeders, executor, registry);
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphRuntimeApiController")
    public ActionGraphRuntimeApiController actionGraphRuntimeApiController(
            ActionGraphRuntimeApiService apiService,
            ActionGraphRuntimeApiProperties properties
    ) {
        return new ActionGraphRuntimeApiController(apiService, properties);
    }
}
