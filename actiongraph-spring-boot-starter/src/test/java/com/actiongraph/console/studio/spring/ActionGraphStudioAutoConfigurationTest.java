package com.actiongraph.console.studio.spring;

import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.llm.LlmClient;
import com.actiongraph.llm.LlmRequest;
import com.actiongraph.llm.LlmResponse;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphStudioAutoConfigurationTest {
    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class,
                    ActionGraphStudioAutoConfiguration.class
            ))
            .withBean(DemoWorkflow.class)
            .withBean(FakeLlmClient.class, () -> new FakeLlmClient(validDraft()));

    @Test
    void studioEndpointIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(ActionGraphStudioController.class);
            assertThat(context).doesNotHaveBean(com.actiongraph.console.studio.GoalStudioService.class);
        });
    }

    @Test
    void studioEndpointDraftsGoalAndRequiresConfiguredToken() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.studio.enabled=true",
                        "actiongraph.studio.shared-secret=studio-secret"
                )
                .run(context -> {
                    MockMvc mockMvc = MockMvcBuilders.webAppContextSetup(context).build();

                    mockMvc.perform(post("/actiongraph/studio/sessions")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"description\":\"create a demo capability\"}"))
                            .andExpect(status().isUnauthorized());

                    mockMvc.perform(post("/actiongraph/studio/sessions")
                                    .header("X-ActionGraph-Studio-Token", "studio-secret")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"description\":\"create a demo capability\"}"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.status").value("DRAFT_VALID"))
                            .andExpect(jsonPath("$.reachable").value(true))
                            .andExpect(jsonPath("$.previewPlan[0]").value("studio.load"))
                            .andExpect(jsonPath("$.riskProfile[1].actionId").value("studio.finish"))
                            .andExpect(jsonPath("$.riskProfile[1].riskLevel").value("HIGH"))
                            .andExpect(jsonPath("$.riskProfile[1].requiresHumanReview").value(true));
                });
    }

    @Test
    void studioEnabledInForbiddenProfileFailsStartup() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.studio.enabled=true",
                        "spring.profiles.active=prod"
                )
                .run(context -> {
                    assertThat(context).hasFailed();
                    assertThat(context.getStartupFailure())
                            .hasMessageContaining("actiongraph.studio.enabled=true is forbidden")
                            .hasMessageContaining("prod");
                });
    }

    private static String validDraft() {
        return """
                {
                  "type": "studio.generated",
                  "description": "Generated studio goal",
                  "targetConditions": ["studio:DONE"],
                  "seedConditions": ["studio:START"],
                  "parameters": []
                }
                """;
    }

    static final class DemoWorkflow {
        @ActionGraphAction(
                id = "studio.load",
                description = "Load studio context",
                preconditions = "studio:START",
                effects = "studio:LOADED"
        )
        ActionResult load() {
            return ActionResult.ok();
        }

        @ActionGraphAction(
                id = "studio.finish",
                description = "Finish with approval",
                preconditions = "studio:LOADED",
                effects = "studio:DONE",
                riskLevel = ActionRiskLevel.HIGH,
                requiresHumanReview = true
        )
        ActionResult finish() {
            return ActionResult.ok();
        }
    }

    static final class FakeLlmClient implements LlmClient {
        private final List<String> responses;
        private final List<LlmRequest> requests = new ArrayList<>();
        private int index;

        FakeLlmClient(String... responses) {
            this.responses = List.of(responses);
        }

        @Override
        public LlmResponse complete(LlmRequest request) {
            requests.add(request);
            String response = responses.get(Math.min(index, responses.size() - 1));
            index++;
            return new LlmResponse(response);
        }
    }
}
