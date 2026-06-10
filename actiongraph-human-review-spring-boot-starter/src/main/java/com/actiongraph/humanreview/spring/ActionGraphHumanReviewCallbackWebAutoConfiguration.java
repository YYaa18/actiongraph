package com.actiongraph.humanreview.spring;

import com.actiongraph.policy.HumanReviewCallbackHandler;
import com.actiongraph.policy.HumanReviewRepository;
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
@ConditionalOnBean(HumanReviewRepository.class)
@ConditionalOnProperty(
        prefix = "actiongraph.human-review.callback-endpoint",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphHumanReviewCallbackProperties.class)
public class ActionGraphHumanReviewCallbackWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public HumanReviewCallbackHandler actionGraphHumanReviewCallbackHandler(
            HumanReviewRepository humanReviewRepository
    ) {
        return new HumanReviewCallbackHandler(humanReviewRepository);
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphHumanReviewCallbackController")
    public ActionGraphHumanReviewCallbackController actionGraphHumanReviewCallbackController(
            HumanReviewCallbackHandler humanReviewCallbackHandler,
            ActionGraphHumanReviewCallbackProperties properties
    ) {
        return new ActionGraphHumanReviewCallbackController(
                humanReviewCallbackHandler,
                properties
        );
    }
}
