package com.actiongraph.controlplane.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.catalog.spring.ActionGraphComponentCatalogController;
import com.actiongraph.catalog.spring.ActionGraphComponentCatalogWebAutoConfiguration;
import com.actiongraph.console.ConsoleRunPage;
import com.actiongraph.console.ConsoleRunQuery;
import com.actiongraph.console.ConsoleRunRepository;
import com.actiongraph.console.ConsoleRunSummary;
import com.actiongraph.console.export.spring.ActionGraphConsoleExportAutoConfiguration;
import com.actiongraph.console.export.spring.ActionGraphConsoleExportController;
import com.actiongraph.console.spring.ActionGraphConsoleApiAutoConfiguration;
import com.actiongraph.console.spring.ActionGraphConsoleApiController;
import com.actiongraph.console.spring.ActionGraphConsolePageController;
import com.actiongraph.console.spring.ActionGraphConsoleServiceAutoConfiguration;
import com.actiongraph.console.spring.ActionGraphConsoleUiAutoConfiguration;
import com.actiongraph.humanreview.api.spring.ActionGraphHumanReviewApiController;
import com.actiongraph.humanreview.api.spring.ActionGraphHumanReviewApiWebAutoConfiguration;
import com.actiongraph.humanreview.spring.ActionGraphHumanReviewCallbackController;
import com.actiongraph.humanreview.spring.ActionGraphHumanReviewCallbackWebAutoConfiguration;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalBlackboardSeederRegistry;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.Plan;
import com.actiongraph.planning.PlanStep;
import com.actiongraph.policy.ApprovalChain;
import com.actiongraph.policy.ApprovalStage;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.InMemoryHumanReviewRepository;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.api.spring.ActionGraphRuntimeApiController;
import com.actiongraph.runtime.api.spring.ActionGraphRuntimeApiWebAutoConfiguration;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import com.actiongraph.trace.TraceEvent;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.TraceHasher;
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

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class ActionGraphControlPlaneSpringBootStarterTest {
    private static final GoalType GOAL_TYPE = new GoalType("control-plane:finish");
    private static final Condition INPUT_PRESENT = Condition.of("control-plane:INPUT_PRESENT");
    private static final Condition DONE = Condition.of("control-plane:DONE");
    private static final ActionId REVIEW_ACTION = new ActionId("control-plane.review");

    private final WebApplicationContextRunner contextRunner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    JacksonAutoConfiguration.class,
                    HttpMessageConvertersAutoConfiguration.class,
                    WebMvcAutoConfiguration.class,
                    ActionGraphComponentCatalogWebAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class,
                    ActionGraphRuntimeApiWebAutoConfiguration.class,
                    ActionGraphHumanReviewApiWebAutoConfiguration.class,
                    ActionGraphHumanReviewCallbackWebAutoConfiguration.class,
                    ActionGraphConsoleServiceAutoConfiguration.class,
                    ActionGraphConsoleApiAutoConfiguration.class,
                    ActionGraphConsoleUiAutoConfiguration.class,
                    ActionGraphConsoleExportAutoConfiguration.class
            ))
            .withBean(GoalInterpreter.class, TestGoalInterpreter::new)
            .withBean(GoalBlackboardSeederRegistry.class, ActionGraphControlPlaneSpringBootStarterTest::seeders)
            .withBean(Action.class, () -> new FinishAction(new AtomicInteger()))
            .withBean(HumanReviewRepository.class, InMemoryHumanReviewRepository::new)
            .withBean(ConsoleRunRepository.class, InMemoryConsoleRunRepository::new)
            .withPropertyValues(
                    "actiongraph.runtime.api.enabled=true",
                    "actiongraph.component-catalog.enabled=true",
                    "actiongraph.human-review.api.enabled=true",
                    "actiongraph.human-review.callback-endpoint.enabled=true",
                    "actiongraph.console.enabled=true"
            );

    @Test
    void aggregateComposesRuntimeReviewCallbackAndConsoleEndpoints() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(ActionGraphRuntimeApiController.class);
            assertThat(context).hasSingleBean(ActionGraphComponentCatalogController.class);
            assertThat(context).hasSingleBean(ActionGraphHumanReviewApiController.class);
            assertThat(context).hasSingleBean(ActionGraphHumanReviewCallbackController.class);
            assertThat(context).hasSingleBean(ActionGraphConsoleApiController.class);
            assertThat(context).hasSingleBean(ActionGraphConsolePageController.class);
            assertThat(context).hasSingleBean(ActionGraphConsoleExportController.class);

            savePending(context.getBean(HumanReviewRepository.class));
            seedConsole(context.getBean(InMemoryConsoleRunRepository.class));

            MockMvc mockMvc = mockMvc(context);
            mockMvc.perform(post("/actiongraph/runtime/runs")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "input": "finish",
                                      "knownParameters": {"id": "I-1"}
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.disposition").value("RUN_STARTED"))
                    .andExpect(jsonPath("$.run.status").value("COMPLETED"))
                    .andExpect(jsonPath("$.run.executedActions[0]").value("control-plane.finish"));

            mockMvc.perform(get("/actiongraph/components/profiles/full-control-plane")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.modules[0]").value("actiongraph-control-plane-spring-boot-starter"));

            mockMvc.perform(get("/actiongraph/components/modules/actiongraph-control-plane-api/profiles")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[*].name", hasItem("java8-legacy-client")));

            mockMvc.perform(get("/actiongraph/human-review/tasks/pending"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$[0].runId").value("RUN-REVIEW"))
                    .andExpect(jsonPath("$[0].actionId").value(REVIEW_ACTION.value()));

            mockMvc.perform(post("/actiongraph/human-review/callbacks")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("""
                                    {
                                      "runId": "RUN-REVIEW",
                                      "actionId": "control-plane.review",
                                      "expectedStageIndex": 0,
                                      "decision": "APPROVED",
                                      "reviewer": "ops-checker",
                                      "comment": "approved"
                                    }
                                    """))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.decision").value("APPROVED"))
                    .andExpect(jsonPath("$.stageDecisionCount").value(1));

            mockMvc.perform(get("/actiongraph/console")
                            .accept(MediaType.TEXT_HTML))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("ActionGraph Console")));

            mockMvc.perform(get("/actiongraph/console/runs")
                            .accept(MediaType.APPLICATION_JSON))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.runs[0].runId").value("RUN-CONSOLE"));

            mockMvc.perform(get("/actiongraph/console/runs/export.csv"))
                    .andExpect(status().isOk())
                    .andExpect(content().string(containsString("RUN-CONSOLE")));
        });
    }

    @Test
    void aggregateDoesNotPullRuntimeOrDomainStorageStarters() {
        assertLoadable("com.actiongraph.runtime.api.spring.ActionGraphRuntimeApiController");
        assertLoadable("com.actiongraph.catalog.spring.ActionGraphComponentCatalogController");
        assertLoadable("com.actiongraph.humanreview.api.spring.ActionGraphHumanReviewApiController");
        assertLoadable("com.actiongraph.humanreview.spring.ActionGraphHumanReviewCallbackController");
        assertLoadable("com.actiongraph.console.spring.ActionGraphConsoleApiController");
        assertLoadable("com.actiongraph.console.spring.ActionGraphConsolePageController");
        assertLoadable("com.actiongraph.console.export.spring.ActionGraphConsoleExportController");

        assertNotLoadable("com.actiongraph.jdbc.spring.ActionGraphJdbcAutoConfiguration");
        assertNotLoadable("com.actiongraph.memory.jdbc.spring.ActionGraphMemoryJdbcAutoConfiguration");
        assertNotLoadable("com.actiongraph.humanreview.jdbc.spring.ActionGraphHumanReviewJdbcAutoConfiguration");
        assertLoadable("com.actiongraph.console.jdbc.spring.ActionGraphConsoleJdbcAutoConfiguration");
        assertNotLoadable("com.actiongraph.humanreview.spring.ActionGraphHumanReviewAutoConfiguration");
    }

    private static void savePending(HumanReviewRepository repository) {
        repository.savePending(HumanReviewTask.pending(
                new HumanReviewRequest(
                        "RUN-REVIEW",
                        REVIEW_ACTION,
                        ActionRiskLevel.HIGH,
                        true,
                        new Plan(List.of(new PlanStep(REVIEW_ACTION))),
                        Set.of(Condition.of("control-plane:READY")),
                        Map.of("requestId", "REQ-1"),
                        Map.of("amount", "1000")
                ),
                "Review required",
                new ApprovalChain(List.of(new ApprovalStage("checker", "ops")))
        ));
    }

    private static GoalBlackboardSeederRegistry seeders() {
        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        seeders.register(new TestSeeder());
        return seeders;
    }

    private static void seedConsole(InMemoryConsoleRunRepository repository) {
        TraceEvent started = hashed("RUN-CONSOLE", 1, "", Instant.parse("2026-06-10T10:00:00Z"),
                TraceEventType.RUN_STARTED, Map.of());
        TraceEvent ended = hashed("RUN-CONSOLE", 2, started.hash(), Instant.parse("2026-06-10T10:00:01Z"),
                TraceEventType.RUN_ENDED, Map.of("status", "COMPLETED"));
        repository.save(new ConsoleRunSummary(
                "RUN-CONSOLE",
                started.at(),
                ended.at(),
                "COMPLETED",
                2,
                true,
                0,
                "Trace chain is valid"
        ), List.of(started, ended));
    }

    private static TraceEvent hashed(
            String runId,
            long seq,
            String prevHash,
            Instant at,
            TraceEventType type,
            Map<String, String> data
    ) {
        return new TraceEvent(
                runId,
                seq,
                at,
                type,
                null,
                type.name(),
                data,
                prevHash,
                TraceHasher.hash(runId, seq, at, type, null, type.name(), data, prevHash)
        );
    }

    private MockMvc mockMvc(WebApplicationContext context) {
        return MockMvcBuilders.webAppContextSetup(context).build();
    }

    private static void assertLoadable(String className) {
        assertThat(load(className)).as(className).isTrue();
    }

    private static void assertNotLoadable(String className) {
        assertThat(load(className)).as(className).isFalse();
    }

    private static boolean load(String className) {
        try {
            Class.forName(className);
            return true;
        } catch (ClassNotFoundException ex) {
            return false;
        }
    }

    private static final class TestGoalInterpreter implements GoalInterpreter {
        @Override
        public GoalInterpretation interpret(String input) {
            return interpret(input, GoalParameters.empty());
        }

        @Override
        public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
            return GoalInterpretation.ready(
                    GOAL_TYPE,
                    knownParameters,
                    new Goal("finish control-plane test workflow", Set.of(DONE))
            );
        }
    }

    private static final class TestSeeder implements GoalBlackboardSeeder {
        @Override
        public GoalType goalType() {
            return GOAL_TYPE;
        }

        @Override
        public void seed(GoalParameters parameters, Blackboard blackboard) {
            blackboard.put(new InputId(parameters.get("id").orElse("I-1")));
            blackboard.addCondition(INPUT_PRESENT);
        }
    }

    private record InputId(String value) {
    }

    private static final class FinishAction implements Action {
        private final AtomicInteger executions;

        private FinishAction(AtomicInteger executions) {
            this.executions = executions;
        }

        @Override
        public ActionId id() {
            return new ActionId("control-plane.finish");
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
            return ActionRiskLevel.LOW;
        }

        @Override
        public boolean requiresHumanReview() {
            return false;
        }

        @Override
        public ActionResult execute(ExecutionContext context) {
            executions.incrementAndGet();
            return ActionResult.ok();
        }
    }

    static final class InMemoryConsoleRunRepository implements ConsoleRunRepository {
        private final Map<String, ConsoleRunSummary> summaries = new LinkedHashMap<>();
        private final Map<String, List<TraceEvent>> traces = new LinkedHashMap<>();

        void save(ConsoleRunSummary summary, List<TraceEvent> trace) {
            summaries.put(summary.runId(), summary);
            traces.put(summary.runId(), List.copyOf(trace));
        }

        @Override
        public ConsoleRunPage findRuns(ConsoleRunQuery query) {
            Objects.requireNonNull(query, "query");
            List<ConsoleRunSummary> matching = summaries.values()
                    .stream()
                    .sorted(Comparator.comparing(ConsoleRunSummary::lastEventAt).reversed())
                    .toList();
            int total = matching.size();
            int from = Math.min(query.offset(), total);
            int to = Math.min(total, from + query.limit());
            return new ConsoleRunPage(query.limit(), query.offset(), total,
                    new ArrayList<>(matching.subList(from, to)));
        }

        @Override
        public Optional<ConsoleRunSummary> findRun(String runId) {
            return Optional.ofNullable(summaries.get(runId));
        }

        @Override
        public List<TraceEvent> findTraceEvents(String runId) {
            return traces.getOrDefault(runId, List.of());
        }
    }
}
