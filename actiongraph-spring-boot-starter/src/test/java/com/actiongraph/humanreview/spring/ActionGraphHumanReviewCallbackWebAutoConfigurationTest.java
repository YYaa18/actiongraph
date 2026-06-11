package com.actiongraph.humanreview.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import com.actiongraph.policy.HumanReviewCallbackHandler;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphHumanReviewCallbackWebAutoConfigurationTest {
    private static final ActionId ACTION_ID = new ActionId("claim.approval.request");

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphHumanReviewCallbackWebAutoConfiguration.class
            ))
            .withBean(HumanReviewRepository.class, InMemoryHumanReviewRepository::new);

    private final WebApplicationContextRunner contextRunnerWithoutRepository = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphHumanReviewCallbackWebAutoConfiguration.class
            ));

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphHumanReviewCallbackWebAutoConfiguration.class
            ))
            .withBean(HumanReviewRepository.class, InMemoryHumanReviewRepository::new);

    private final WebApplicationContextRunner runtimeStarterCompositionRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphHumanReviewAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class,
                    ActionGraphHumanReviewCallbackWebAutoConfiguration.class
            ));

    @Test
    void callbackEndpointIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(HumanReviewCallbackHandler.class);
            assertThat(context).doesNotHaveBean(ActionGraphHumanReviewCallbackController.class);
        });
    }

    @Test
    void callbackEndpointApprovesPendingReviewTask() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.callback-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(ActionGraphHumanReviewCallbackController.class);

                    savePending(context.getBean(HumanReviewRepository.class));

                    mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "runId": "RUN-1",
                                              "actionId": "claim.approval.request",
                                              "expectedStageIndex": 0,
                                              "decision": "APPROVED",
                                              "reviewer": "claims-checker",
                                              "comment": "approved"
                                            }
                                            """))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.runId").value("RUN-1"))
                            .andExpect(jsonPath("$.actionId").value("claim.approval.request"))
                            .andExpect(jsonPath("$.decision").value("APPROVED"))
                            .andExpect(jsonPath("$.reviewer").value("claims-checker"))
                            .andExpect(jsonPath("$.stageDecisionCount").value(1));

                    assertThat(context.getBean(HumanReviewRepository.class).find("RUN-1", ACTION_ID))
                            .get()
                            .satisfies(task -> assertThat(task.decision()).isEqualTo(HumanReviewDecision.APPROVED));
                });
    }

    @Test
    void callbackEndpointIsNotCreatedOutsideWebApplications() {
        nonWebContextRunner
                .withPropertyValues("actiongraph.human-review.callback-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HumanReviewCallbackHandler.class);
                    assertThat(context).doesNotHaveBean(ActionGraphHumanReviewCallbackController.class);
                });
    }

    @Test
    void callbackEndpointIsNotCreatedWithoutHumanReviewRepository() {
        contextRunnerWithoutRepository
                .withPropertyValues("actiongraph.human-review.callback-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HumanReviewCallbackHandler.class);
                    assertThat(context).doesNotHaveBean(ActionGraphHumanReviewCallbackController.class);
                });
    }

    @Test
    void callbackEndpointComposesWithRuntimeStarterRepository() {
        runtimeStarterCompositionRunner
                .withPropertyValues("actiongraph.human-review.callback-endpoint.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(HumanReviewRepository.class);
                    assertThat(context).hasSingleBean(HumanReviewCallbackHandler.class);
                    assertThat(context).hasSingleBean(ActionGraphHumanReviewCallbackController.class);
                });
    }

    @Test
    void callbackEndpointCanUseCustomPath() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.human-review.callback-endpoint.enabled=true",
                        "actiongraph.human-review.callback-endpoint.path=/internal/reviews"
                )
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class));

                    mockMvc(context).perform(post("/internal/reviews")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "runId": "RUN-1",
                                              "actionId": "claim.approval.request",
                                              "expectedStageIndex": 0,
                                              "decision": "DENIED",
                                              "reviewer": "risk-officer",
                                              "comment": "blocked"
                                            }
                                            """))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.decision").value("DENIED"))
                            .andExpect(jsonPath("$.reviewer").value("risk-officer"));
                });
    }

    @Test
    void callbackEndpointReturnsConflictForAlreadyDecidedStage() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.callback-endpoint.enabled=true")
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class));

                    mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(approvedCallback()))
                            .andExpect(status().isOk());

                    mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(approvedCallback()))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.error").value("CONFLICT"));
                });
    }

    @Test
    void callbackEndpointReturnsNotFoundForMissingTask() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.callback-endpoint.enabled=true")
                .run(context -> mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(approvedCallback()))
                        .andExpect(status().isNotFound())
                        .andExpect(jsonPath("$.error").value("NOT_FOUND")));
    }

    @Test
    void callbackEndpointReturnsBadRequestForPendingDecision() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.callback-endpoint.enabled=true")
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class));

                    mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "runId": "RUN-1",
                                              "actionId": "claim.approval.request",
                                              "expectedStageIndex": 0,
                                              "decision": "PENDING",
                                              "reviewer": "claims-checker",
                                              "comment": "not final"
                                            }
                                            """))
                            .andExpect(status().isBadRequest())
                            .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
                });
    }

    @Test
    void callbackEndpointRequiresTokenWhenSharedSecretIsConfigured() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.human-review.callback-endpoint.enabled=true",
                        "actiongraph.human-review.callback-endpoint.shared-secret=review-secret"
                )
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class));

                    mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(approvedCallback()))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    assertThat(context.getBean(HumanReviewRepository.class).find("RUN-1", ACTION_ID))
                            .get()
                            .satisfies(task -> assertThat(task.decision()).isEqualTo(HumanReviewDecision.PENDING));
                });
    }

    @Test
    void callbackEndpointRejectsInvalidToken() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.human-review.callback-endpoint.enabled=true",
                        "actiongraph.human-review.callback-endpoint.shared-secret=review-secret"
                )
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class));

                    mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                    .header("X-ActionGraph-Review-Token", "wrong-secret")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(approvedCallback()))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    assertThat(context.getBean(HumanReviewRepository.class).find("RUN-1", ACTION_ID))
                            .get()
                            .satisfies(task -> assertThat(task.decision()).isEqualTo(HumanReviewDecision.PENDING));
                });
    }

    @Test
    void callbackEndpointAcceptsCustomTokenHeader() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.human-review.callback-endpoint.enabled=true",
                        "actiongraph.human-review.callback-endpoint.token-header=X-Claims-Review-Token",
                        "actiongraph.human-review.callback-endpoint.shared-secret=review-secret"
                )
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class));

                    mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                    .header("X-Claims-Review-Token", "review-secret")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(approvedCallback()))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.decision").value("APPROVED"))
                            .andExpect(jsonPath("$.reviewer").value("claims-checker"));
                });
    }

    private MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void savePending(HumanReviewRepository repository) {
        repository.savePending(HumanReviewTask.pending(request(), "Review required"));
    }

    private HumanReviewRequest request() {
        return new HumanReviewRequest(
                "RUN-1",
                ACTION_ID,
                ActionRiskLevel.HIGH,
                true,
                new Plan(List.of(new PlanStep(ACTION_ID))),
                Set.of(Condition.of("claims:READY")),
                Map.of("claimId", "CLM100")
        );
    }

    private String approvedCallback() {
        return """
                {
                  "runId": "RUN-1",
                  "actionId": "claim.approval.request",
                  "expectedStageIndex": 0,
                  "decision": "APPROVED",
                  "reviewer": "claims-checker",
                  "comment": "approved"
                }
                """;
    }
}
