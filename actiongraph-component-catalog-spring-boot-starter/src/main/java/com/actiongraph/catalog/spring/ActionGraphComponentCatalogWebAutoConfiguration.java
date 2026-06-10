package com.actiongraph.catalog.spring;

import com.actiongraph.catalog.ActionGraphComponentCatalogService;
import org.springframework.boot.autoconfigure.AutoConfiguration;
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
        prefix = "actiongraph.component-catalog",
        name = "enabled",
        havingValue = "true"
)
@EnableConfigurationProperties(ActionGraphComponentCatalogProperties.class)
public class ActionGraphComponentCatalogWebAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ActionGraphComponentCatalogService actionGraphComponentCatalogService() {
        return ActionGraphComponentCatalogService.defaultCatalog();
    }

    @Bean
    @ConditionalOnMissingBean(name = "actionGraphComponentCatalogController")
    public ActionGraphComponentCatalogController actionGraphComponentCatalogController(
            ActionGraphComponentCatalogService catalogService,
            ActionGraphComponentCatalogProperties properties
    ) {
        return new ActionGraphComponentCatalogController(catalogService, properties);
    }
}
