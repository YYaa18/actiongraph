package com.actiongraph.console.studio.spring;

import com.actiongraph.action.ActionRegistry;
import com.actiongraph.api.Experimental;
import com.actiongraph.console.studio.GoalStudioLanguageModel;
import com.actiongraph.console.studio.GoalStudioService;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.annotation.GoalValueConverterResolver;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinitionFactory;
import com.actiongraph.llm.LlmClient;
import com.actiongraph.llm.LlmRequest;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifiers;
import com.actiongraph.spring.SpringGoalValueConverterResolver;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@AutoConfiguration(afterName = "com.actiongraph.spring.ActionGraphAutoConfiguration")
@ConditionalOnProperty(prefix = "actiongraph.studio", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(ActionGraphStudioProperties.class)
@Experimental(
        since = "0.2.0",
        value = "Goal Studio auto-configuration is experimental and intended for non-production drafting environments."
)
public class ActionGraphStudioAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(LlmClient.class)
    public GoalStudioService actionGraphGoalStudioService(
            LlmClient llmClient,
            GoalCatalog catalog,
            ActionRegistry registry,
            ConfigurableListableBeanFactory beanFactory,
            ActionGraphStudioProperties properties,
            Environment environment
    ) {
        assertNotForbiddenProfile(properties, environment);
        GoalValueConverterResolver converterResolver =
                new SpringGoalValueConverterResolver(beanFactory);
        GoalStudioLanguageModel languageModel = (systemPrompt, userPrompt, maxTokens) ->
                llmClient.complete(new LlmRequest(systemPrompt, userPrompt, maxTokens)).text();
        return new GoalStudioService(
                languageModel,
                catalog,
                registry,
                new ConfiguredGoalDefinitionFactory(converterResolver),
                properties.getMaxAutoRepairs(),
                properties.getBundleDirectory(),
                properties.getSourceEnv()
        );
    }

    @Bean
    @ConditionalOnMissingBean
    @ConditionalOnBean(GoalStudioService.class)
    @ConditionalOnClass(name = {
            "org.springframework.web.bind.annotation.RestController",
            "org.springframework.web.servlet.DispatcherServlet"
    })
    @ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
    public ActionGraphStudioController actionGraphStudioController(
            GoalStudioService studioService,
            ActionGraphStudioProperties properties,
            ObjectProvider<ActionGraphEndpointAccessVerifier> accessVerifier
    ) {
        return new ActionGraphStudioController(
                studioService,
                properties,
                ActionGraphEndpointAccessVerifiers.getOrSharedSecretDefault(accessVerifier)
        );
    }

    private void assertNotForbiddenProfile(ActionGraphStudioProperties properties, Environment environment) {
        Set<String> forbidden = properties.getForbiddenProfiles().stream()
                .map(profile -> profile.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        for (String profile : environment.getActiveProfiles()) {
            if (forbidden.contains(profile.toLowerCase(Locale.ROOT))) {
                throw new ActionGraphConfigurationException(
                        "actiongraph.studio.enabled=true is forbidden for active profile: " + profile
                                + ". Forbidden profiles: " + Arrays.toString(environment.getActiveProfiles()));
            }
        }
    }
}
