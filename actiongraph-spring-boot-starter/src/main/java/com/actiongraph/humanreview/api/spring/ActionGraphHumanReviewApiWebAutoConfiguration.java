package com.actiongraph.humanreview.api.spring;

import com.actiongraph.humanreview.api.HumanReviewApiService;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifier;
import com.actiongraph.spring.security.ActionGraphEndpointAccessVerifiers;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration(afterName = "com.actiongraph.humanreview.spring.ActionGraphHumanReviewAutoConfiguration")
@ConditionalOnClass(name = {
        "org.springframework.web.bind.annotation.RestController",
        "org.springframework.web.servlet.DispatcherServlet"
})
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnBean(HumanReviewRepository.class)
@ConditionalOnProperty(
        prefix = "actiongraph.human-review.api",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphHumanReviewApiProperties.class)
public class ActionGraphHumanReviewApiWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public HumanReviewApiService actionGraphHumanReviewApiService(HumanReviewRepository repository) {
        return new HumanReviewApiService(repository);
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphHumanReviewApiController")
    public ActionGraphHumanReviewApiController actionGraphHumanReviewApiController(
            HumanReviewApiService apiService,
            ActionGraphHumanReviewApiProperties properties,
            ObjectProvider<ActionGraphEndpointAccessVerifier> accessVerifier
    ) {
        return new ActionGraphHumanReviewApiController(
                apiService,
                properties,
                ActionGraphEndpointAccessVerifiers.getOrSharedSecretDefault(accessVerifier)
        );
    }
}
