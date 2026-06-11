package com.actiongraph.runtime.api.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.api.ActionGraphRuntimeApiService;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.http.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration;
import org.springframework.boot.autoconfigure.web.servlet.WebMvcAutoConfiguration;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphRuntimeApiWebAutoConfigurationTest {
    private static final GoalType TYPE = new GoalType("runtime-api-spring-test:finish");
    private static final Condition INPUT_PRESENT = Condition.of("runtime-api-spring-test:INPUT_PRESENT");
    private static final Condition DONE = Condition.of("runtime-api-spring-test:DONE");

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class,
                    ActionGraphRuntimeApiWebAutoConfiguration.class
            ))
            .withBean(GoalInterpreter.class, TestInterpreter::new)
            .withBean(GoalBlackboardSeederRegistry.class, ActionGraphRuntimeApiWebAutoConfigurationTest::seeders);

    private final WebApplicationContextRunner contextRunnerWithoutInterpreter = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class,
                    ActionGraphRuntimeApiWebAutoConfiguration.class
            ))
            .withBean(GoalBlackboardSeederRegistry.class, ActionGraphRuntimeApiWebAutoConfigurationTest::seeders)
            .withBean(Action.class, () -> new FinishAction(false, new AtomicInteger()));

    @Test
    void runtimeApiIsDisabledByDefault() {
        contextRunner
                .withBean(Action.class, () -> new FinishAction(false, new AtomicInteger()))
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ActionGraphRuntimeApiService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphRuntimeApiController.class);
                });
    }

    @Test
    void runtimeApiIsNotCreatedWithoutInterpretationBeans() {
        contextRunnerWithoutInterpreter
                .withPropertyValues("actiongraph.runtime.api.enabled=true")
                .run(context -> {
                    assertThat(context).doesNotHaveBean(ActionGraphRuntimeApiService.class);
                    assertThat(context).doesNotHaveBean(ActionGraphRuntimeApiController.class);
                });
    }

    @Test
    void interpretsClarificationAndRequiresConfiguredToken() {
        contextRunner
                .withBean(Action.class, () -> new FinishAction(false, new AtomicInteger()))
                .withPropertyValues(
                        "actiongraph.runtime.api.enabled=true",
                        "actiongraph.runtime.api.shared-secret=runtime-secret"
                )
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(post("/actiongraph/runtime/interpret")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"input":"finish"}
                                            """))
                            .andExpect(status().isUnauthorized())
                            .andExpect(jsonPath("$.error").value("UNAUTHORIZED"));

                    mockMvc.perform(post("/actiongraph/runtime/interpret")
                                    .header("X-ActionGraph-Runtime-Token", "runtime-secret")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {"input":"finish"}
                                            """))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.ready").value(false))
                            .andExpect(jsonPath("$.missingFields[0]").value("id"))
                            .andExpect(jsonPath("$.clarificationQuestion").value("Which id should be processed?"));
                });
    }

    @Test
    void startsReadyGoalFromHttpRequest() {
        AtomicInteger executions = new AtomicInteger();
        contextRunner
                .withBean(Action.class, () -> new FinishAction(false, executions))
                .withPropertyValues("actiongraph.runtime.api.enabled=true")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    String response = mockMvc.perform(post("/actiongraph/runtime/runs")
                                    .header("X-Request-Id", "REQ-SPRING-1")
                                    .header("X-Source-System", "legacy-crm")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "input": "finish",
                                              "knownParameters": {"id": "I-1"}
                                            }
                                            """))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.disposition").value("RUN_STARTED"))
                            .andExpect(jsonPath("$.interpretation.ready").value(true))
                            .andExpect(jsonPath("$.run.status").value("COMPLETED"))
                            .andExpect(jsonPath("$.run.executedActions[0]").value("runtime-api-spring-test.finish"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    String runId = response.replaceAll(".*\\\"runId\\\":\\\"([^\\\"]+)\\\".*", "$1");

                    TraceRepository traceRepository = context.getBean(TraceRepository.class);
                    assertThat(traceRepository.findByRun(runId))
                            .filteredOn(event -> event.type() == TraceEventType.RUN_STARTED)
                            .singleElement()
                            .satisfies(event -> assertThat(event.data())
                                    .containsEntry("requestHeader.X-Request-Id", "REQ-SPRING-1")
                                    .containsEntry("requestHeader.X-Source-System", "legacy-crm"));

                    assertThat(executions).hasValue(1);
                });
    }

    @Test
    void customTraceHeadersControlWhichHeadersEnterRunTrace() {
        contextRunner
                .withBean(Action.class, () -> new FinishAction(false, new AtomicInteger()))
                .withPropertyValues(
                        "actiongraph.runtime.api.enabled=true",
                        "actiongraph.runtime.api.trace-headers[0]=X-Transaction-Id"
                )
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    String response = mockMvc.perform(post("/actiongraph/runtime/runs")
                                    .header("X-Request-Id", "REQ-IGNORED")
                                    .header("X-Transaction-Id", "TX-1001")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "input": "finish",
                                              "knownParameters": {"id": "I-1"}
                                            }
                                            """))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    String runId = response.replaceAll(".*\\\"runId\\\":\\\"([^\\\"]+)\\\".*", "$1");

                    TraceRepository traceRepository = context.getBean(TraceRepository.class);
                    assertThat(traceRepository.findByRun(runId))
                            .filteredOn(event -> event.type() == TraceEventType.RUN_STARTED)
                            .singleElement()
                            .satisfies(event -> assertThat(event.data())
                                    .containsEntry("requestHeader.X-Transaction-Id", "TX-1001")
                                    .doesNotContainKey("requestHeader.X-Request-Id"));
                });
    }

    @Test
    void runtimeTokenHeaderIsNeverCopiedToRunTraceMetadata() {
        contextRunner
                .withBean(Action.class, () -> new FinishAction(false, new AtomicInteger()))
                .withPropertyValues(
                        "actiongraph.runtime.api.enabled=true",
                        "actiongraph.runtime.api.token-header=X-Runtime-Secret",
                        "actiongraph.runtime.api.shared-secret=runtime-secret",
                        "actiongraph.runtime.api.trace-headers[0]=x-runtime-secret",
                        "actiongraph.runtime.api.trace-headers[1]=X-Request-Id"
                )
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    String response = mockMvc.perform(post("/actiongraph/runtime/runs")
                                    .header("X-Runtime-Secret", "runtime-secret")
                                    .header("X-Request-Id", "REQ-SAFE-TRACE-1")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "input": "finish",
                                              "knownParameters": {"id": "I-1"}
                                            }
                                            """))
                            .andExpect(status().isOk())
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    String runId = response.replaceAll(".*\\\"runId\\\":\\\"([^\\\"]+)\\\".*", "$1");

                    TraceRepository traceRepository = context.getBean(TraceRepository.class);
                    assertThat(traceRepository.findByRun(runId))
                            .filteredOn(event -> event.type() == TraceEventType.RUN_STARTED)
                            .singleElement()
                            .satisfies(event -> assertThat(event.data())
                                    .containsEntry("requestHeader.X-Request-Id", "REQ-SAFE-TRACE-1")
                                    .doesNotContainKey("requestHeader.x-runtime-secret")
                                    .doesNotContainValue("runtime-secret"));
                });
    }

    @Test
    void resumesSuspendedRunThroughCustomPath() {
        contextRunner
                .withBean(Action.class, () -> new FinishAction(true, new AtomicInteger()))
                .withPropertyValues(
                        "actiongraph.runtime.api.enabled=true",
                        "actiongraph.runtime.api.path=/internal/actiongraph-runtime"
                )
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);
                    String startBody = mockMvc.perform(post("/internal/actiongraph-runtime/runs")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("""
                                            {
                                              "input": "finish",
                                              "knownParameters": {"id": "I-1"}
                                            }
                                            """))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.run.status").value("SUSPENDED_PENDING_REVIEW"))
                            .andReturn()
                            .getResponse()
                            .getContentAsString();
                    String runId = startBody.replaceAll(".*\\\"runId\\\":\\\"([^\\\"]+)\\\".*", "$1");

                    mockMvc.perform(post("/internal/actiongraph-runtime/runs/{runId}/resume", runId)
                                    .header("X-Request-Id", "REQ-RESUME-SPRING-1"))
                            .andExpect(status().isOk())
                            .andExpect(jsonPath("$.runId").value(runId))
                            .andExpect(jsonPath("$.status").value("SUSPENDED_PENDING_REVIEW"));

                    TraceRepository traceRepository = context.getBean(TraceRepository.class);
                    assertThat(traceRepository.findByRun(runId))
                            .filteredOn(event -> event.type() == TraceEventType.RUN_RESUMED)
                            .singleElement()
                            .satisfies(event -> assertThat(event.data())
                                    .containsEntry("requestHeader.X-Request-Id", "REQ-RESUME-SPRING-1"));
                });
    }

    @Test
    void reportsNotClaimableResumeAsConflictAndDoesNotExposeOtherControlPlanes() {
        contextRunner
                .withBean(Action.class, () -> new FinishAction(false, new AtomicInteger()))
                .withPropertyValues("actiongraph.runtime.api.enabled=true")
                .run(context -> {
                    MockMvc mockMvc = mockMvc(context);

                    mockMvc.perform(post("/actiongraph/runtime/runs/MISSING/resume"))
                            .andExpect(status().isConflict())
                            .andExpect(jsonPath("$.error").value("NOT_CLAIMABLE"));
                    mockMvc.perform(post("/actiongraph/human-review/callbacks")
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content("{}"))
                            .andExpect(status().isNotFound());
                });
    }

    private static GoalBlackboardSeederRegistry seeders() {
        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        seeders.register(new TestSeeder());
        return seeders;
    }

    private MockMvc mockMvc(org.springframework.web.context.WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static final class TestInterpreter implements GoalInterpreter {
        @Override
        public GoalInterpretation interpret(String input) {
            return interpret(input, GoalParameters.empty());
        }

        @Override
        public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
            if (knownParameters.get("id").isEmpty()) {
                return GoalInterpretation.needsClarification(
                        TYPE,
                        knownParameters,
                        Set.of(new MissingField("id")),
                        new ClarificationQuestion("Which id should be processed?")
                );
            }
            return GoalInterpretation.ready(
                    TYPE,
                    knownParameters,
                    new Goal("finish spring runtime API workflow", Set.of(DONE))
            );
        }
    }

    private static final class TestSeeder implements GoalBlackboardSeeder {
        @Override
        public GoalType goalType() {
            return TYPE;
        }

        @Override
        public void seed(GoalParameters parameters, Blackboard blackboard) {
            blackboard.put(new InputId(parameters.get("id").orElseThrow()));
            blackboard.addCondition(INPUT_PRESENT);
        }
    }

    private record InputId(String value) {
    }

    private static final class FinishAction implements Action {
        private final boolean review;
        private final AtomicInteger executions;

        private FinishAction(boolean review, AtomicInteger executions) {
            this.review = review;
            this.executions = executions;
        }

        @Override
        public ActionId id() {
            return new ActionId("runtime-api-spring-test.finish");
        }

        @Override
        public Set<Class<?>> inputTypes() {
            return Set.of(InputId.class);
        }

        @Override
        public Set<Class<?>> outputTypes() {
            return Set.of();
        }

        @Override
        public Set<Condition> preconditions() {
            return Set.of(INPUT_PRESENT);
        }

        @Override
        public Set<Condition> effects() {
            return Set.of(DONE);
        }

        @Override
        public int cost() {
            return 1;
        }

        @Override
        public ActionRiskLevel riskLevel() {
            return review ? ActionRiskLevel.HIGH : ActionRiskLevel.LOW;
        }

        @Override
        public boolean requiresHumanReview() {
            return review;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            executions.incrementAndGet();
            return ActionResult.ok();
        }
    }
}
