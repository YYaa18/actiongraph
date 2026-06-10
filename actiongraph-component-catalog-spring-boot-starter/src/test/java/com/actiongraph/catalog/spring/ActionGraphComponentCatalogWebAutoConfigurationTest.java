package com.actiongraph.catalog.spring;

import com.actiongraph.catalog.ActionGraphComponentCatalogService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphComponentCatalogWebAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphComponentCatalogWebAutoConfiguration.class
            ));

    @Test
    void catalogEndpointIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ActionGraphComponentCatalogService.class);
            assertThat(context).doesNotHaveBean(ActionGraphComponentCatalogController.class);
        });
    }

    @Test
    void exposesDefaultCatalogWhenEnabled() {
        contextRunner
                .withPropertyValues("actiongraph.component-catalog.enabled=true")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/actiongraph/components")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.components[*].module", hasItem("actiongraph-core")))
                            .andExpect(jsonPath("$.components[*].module", hasItem("actiongraph-component-catalog")))
                            .andExpect(jsonPath("$.profiles[*].name", hasItem("full-control-plane")));
                });
    }

    @Test
    void exposesModuleAndProfileLookupsThroughCustomPath() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.component-catalog.enabled=true",
                        "actiongraph.component-catalog.path=/internal/actiongraph/components"
                )
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/internal/actiongraph/components/modules/actiongraph-control-plane-spring-boot-starter"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.kind").value("CONTROL_PLANE"))
                            .andExpect(jsonPath("$.capabilities[0]").value("control-plane-aggregate"));

                    mockMvc.perform(get("/internal/actiongraph/components/profiles/ecosystem-introspection"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.modules[0]").value("actiongraph-component-catalog"))
                            .andExpect(jsonPath("$.modules[1]").value("actiongraph-control-plane-api"))
                            .andExpect(jsonPath("$.modules[2]").value("actiongraph-component-catalog-spring-boot-starter"));

                    mockMvc.perform(get("/internal/actiongraph/components/profiles/control-plane-response-contracts"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.modules[0]").value("actiongraph-control-plane-api"));

                    mockMvc.perform(get("/internal/actiongraph/components/profiles/control-plane-shared" + "-auth"))
                            .andExpect(status().isNotFound());
                });
    }

    @Test
    void returnsNotFoundForMissingEntries() {
        contextRunner
                .withPropertyValues("actiongraph.component-catalog.enabled=true")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/actiongraph/components/modules/missing"))
                            .andExpect(status().isNotFound())
                            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
                    mockMvc.perform(get("/actiongraph/components/profiles/missing"))
                            .andExpect(status().isNotFound())
                            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
                });
    }

    @Test
    void requiresConfiguredToken() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.component-catalog.enabled=true",
                        "actiongraph.component-catalog.shared-secret=catalog-secret"
                )
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/actiongraph/components/modules"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    mockMvc.perform(get("/actiongraph/components/modules")
                                    .header("X-ActionGraph-Catalog-Token", "catalog-secret"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$[*].module", hasItem("actiongraph-core")));
                });
    }

    private MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }
}
