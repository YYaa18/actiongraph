package com.actiongraph.humanreview.api.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.humanreview.api.HumanReviewApiService;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import com.actiongraph.policy.ApprovalChain;
import com.actiongraph.policy.ApprovalStage;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphHumanReviewApiWebAutoConfigurationTest {
    private static final ActionId ACTION_ID = new ActionId("claim.approval.request");

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphHumanReviewApiWebAutoConfiguration.class
            ))
            .withBean(HumanReviewRepository.class, InMemoryHumanReviewRepository::new);

    private final WebApplicationContextRunner contextRunnerWithoutRepository = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphHumanReviewApiWebAutoConfiguration.class
            ));

    private final ApplicationContextRunner nonWebContextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(ActionGraphHumanReviewApiWebAutoConfiguration.class))
            .withBean(HumanReviewRepository.class, InMemoryHumanReviewRepository::new);

    private final WebApplicationContextRunner runtimeStarterCompositionRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    com.actiongraph.humanreview.spring.ActionGraphHumanReviewAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class,
                    ActionGraphHumanReviewApiWebAutoConfiguration.class
            ));

    @Test
    void humanReviewApiIsDisabledByDefault() {
        contextRunner.run(context -> {
            assertThat(context).doesNotHaveBean(HumanReviewApiService.class);
            assertThat(context).doesNotHaveBean(ActionGraphHumanReviewApiController.class);
        });
    }

    @Test
    void humanReviewApiIsNotCreatedOutsideWebApplications() {
        nonWebContextRunner
                .withPropertyValues("actiongraph.human-review.api.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HumanReviewApiService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphHumanReviewApiController.class);
                });
    }

    @Test
    void humanReviewApiIsNotCreatedWithoutRepository() {
        contextRunnerWithoutRepository
                .withPropertyValues("actiongraph.human-review.api.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(HumanReviewApiService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphHumanReviewApiController.class);
                });
    }

    @Test
    void humanReviewApiComposesWithRuntimeStarterRepository() {
        runtimeStarterCompositionRunner
                .withPropertyValues("actiongraph.human-review.api.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(HumanReviewRepository.class);
                    assertThat(context).hasSingleBean(HumanReviewApiService.class);
                    assertThat(context).hasSingleBean(ActionGraphHumanReviewApiController.class);
                });
    }

    @Test
    void listsPendingTasksAndRequiresConfiguredToken() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.human-review.api.enabled=true",
                        "actiongraph.human-review.api.shared-secret=review-secret"
                )
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class), "RUN-1");
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/actiongraph/human-review/tasks/pending"))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    mockMvc.perform(get("/actiongraph/human-review/tasks/pending")
                                    .header("X-ActionGraph-Review-Token", "review-secret"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$[0].runId").value("RUN-1"))
                            .andExpect(jsonPath("$[0].actionId").value(ACTION_ID.value()))
                            .andExpect(jsonPath("$[0].currentStageName").value("checker"))
                            .andExpect(jsonPath("$[0].attributes.amount").value("120000"));
                });
    }

    @Test
    void readsTasksForRunAndSingleTask() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.api.enabled=true")
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class), "RUN-1");
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(get("/actiongraph/human-review/tasks/runs/RUN-1"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$[0].runId").value("RUN-1"));
                    mockMvc.perform(get("/actiongraph/human-review/tasks/runs/RUN-1/actions/claim.approval.request"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.planPreview[0]").value(ACTION_ID.value()));
                    mockMvc.perform(get("/actiongraph/human-review/tasks/runs/MISSING/actions/claim.approval.request"))
                            .andExpect(status().isNotFound())
                            .andExpect(jsonPath("$.error").value("NOT_FOUND"));
                });
    }

    @Test
    void decidesExpectedStageAndReturnsConflictForRepeatedDecision() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.api.enabled=true")
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class), "RUN-1");
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(post("/actiongraph/human-review/tasks/runs/RUN-1/actions/claim.approval.request/decision")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "expectedStageIndex": 0,
                                              "decision": "APPROVED",
                                              "reviewer": "checker-1",
                                              "comment": "checker approved"
                                            }
                                            """))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.decision").value("PENDING"))
                            .andExpect(jsonPath("$.currentStageIndex").value(1))
                            .andExpect(jsonPath("$.stageDecisions[0].stage").value("checker"));

                    mockMvc.perform(post("/actiongraph/human-review/tasks/runs/RUN-1/actions/claim.approval.request/decision")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "expectedStageIndex": 0,
                                              "decision": "APPROVED",
                                              "reviewer": "checker-1",
                                              "comment": "duplicate"
                                            }
                                            """))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.error").value("CONFLICT"));
                });
    }

    @Test
    void supportsCustomPathAndRejectsPendingDecision() {
        contextRunner
                .withPropertyValues(
                        "actiongraph.human-review.api.enabled=true",
                        "actiongraph.human-review.api.path=/internal/review-tasks"
                )
                .run(context -> {
                    savePending(context.getBean(HumanReviewRepository.class), "RUN-1");
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(post("/internal/review-tasks/runs/RUN-1/actions/claim.approval.request/decision")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "expectedStageIndex": 0,
                                              "decision": "PENDING",
                                              "reviewer": "checker-1",
                                              "comment": "not final"
                                            }
                                            """))
                            .andExpect(status().isBadRequest())
                            .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
                });
    }

    @Test
    void apiStarterDoesNotExposeCallbackEndpoint() {
        contextRunner
                .withPropertyValues("actiongraph.human-review.api.enabled=true")
                .run(context -> mockMvc(context).perform(post("/actiongraph/human-review/callbacks")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{}"))
                        .andExpect(status().isNotFound()));
    }

    private MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private void savePending(HumanReviewRepository repository, String runId) {
        repository.savePending(HumanReviewTask.pending(request(runId), "Review required",
                new ApprovalChain(List.of(
                        new ApprovalStage("checker", "claims-checker"),
                        new ApprovalStage("authorizer", "risk-officer")
                ))));
    }

    private HumanReviewRequest request(String runId) {
        return new HumanReviewRequest(
                runId,
                ACTION_ID,
                ActionRiskLevel.HIGH,
                true,
                new Plan(List.of(new PlanStep(ACTION_ID))),
                Set.of(Condition.of("claims:READY")),
                Map.of("claimId", "CLM100"),
                Map.of("amount", "120000")
        );
    }
}
