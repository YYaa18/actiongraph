package com.actiongraph.console.spring;

import com.actiongraph.console.ActionGraphConsoleService;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

class ActionGraphConsoleUiAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphConsoleServiceAutoConfiguration.class,
                    ActionGraphConsoleUiAutoConfiguration.class
            ));

    @Test
    void consolePageIsDisabledByDefault() {
        contextRunner
                .run(context -> assertThat(context).doesNotHaveBean(ActionGraphConsolePageController.class));
    }

    @Test
    void consolePageCanBeServedWithoutRepositoryOrApi() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.console.enabled=true",
                        "actiongraph.console.path=/internal/actiongraph-console",
                        "actiongraph.console.token-header=X-Internal-Console-Token",
                        "actiongraph.console.default-limit=25",
                        "actiongraph.console.max-limit=75"
                )
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ActionGraphConsoleService.class);
                    assertThat(context).hasSingleBean(ActionGraphConsolePageController.class);
                    mockMvc(context).perform(get("/internal/actiongraph-console")
                                    .accept(MediaType.TEXT_HTML))
                            .andExpect(status().isOk())
                            .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                            .andExpect(content().string(containsString("ActionGraph Console")))
                            .andExpect(content().string(containsString("Run monitoring and trace audit")))
                            .andExpect(content().string(containsString("tokenHeader: 'X-Internal-Console-Token'")))
                            .andExpect(content().string(containsString("defaultLimit: 25")))
                            .andExpect(content().string(containsString("maxLimit: 75")))
                            .andExpect(content().string(containsString("\"/trace\"")));
                });
    }

    @Test
    void consolePageStarterDoesNotExposeJsonApi() {
        contextRunner
                .withPropertyValues("actiongraph.console.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphConsolePageController.class);
                    mockMvc(context).perform(get("/actiongraph/console/runs")
                                    .accept(MediaType.APPLICATION_JSON))
                            .andExpect(status().isNotFound());
                });
    }

    private MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }
}
