package com.actiongraph.runtime.api;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.DefaultActionRegistry;
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
import com.actiongraph.planning.GoapPlanner;
import com.actiongraph.policy.AutoApproveHumanReviewPolicy;
import com.actiongraph.policy.DefaultPolicyGuard;
import com.actiongraph.policy.PendingHumanReviewPolicy;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemorySuspendedRunRepository;
import com.actiongraph.runtime.RunStatus;
import com.actiongraph.runtime.api.batch.BatchGoalInput;
import com.actiongraph.runtime.api.batch.BatchGoalInterpretation;
import com.actiongraph.runtime.api.batch.PerItemBatchGoalInterpreter;
import com.actiongraph.trace.TraceEventType;
import com.actiongraph.trace.InMemoryTraceRepository;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphRuntimeApiServiceTest {
    private static final GoalType TYPE = new GoalType("test:finish");
    private static final Condition INPUT_PRESENT = Condition.of("runtime-api-test:INPUT_PRESENT");
    private static final Condition DONE = Condition.of("runtime-api-test:DONE");

    @Test
    void startsReadyGoalThroughInterpreterSeederAndExecutor() {
        AtomicInteger executions = new AtomicInteger();
        DefaultActionRegistry registry = registry(new FinishAction(false, executions));
        ActionGraphRuntimeApiService service = service(registry, new AutoApproveHumanReviewPolicy(), null);

        RuntimeStartResponse response = service.start("finish", Map.of("id", "I-1"));

        assertThat(response.disposition()).isEqualTo(RuntimeStartDisposition.RUN_STARTED);
        assertThat(response.interpretation().ready()).isTrue();
        assertThat(response.interpretation().parameters()).containsEntry("id", "I-1");
        assertThat(response.interpretation().targetConditions()).containsExactly(DONE.key());
        assertThat(response.run()).hasValueSatisfying(run -> {
            assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
            assertThat(run.finalConditions()).containsExactly(DONE.key(), INPUT_PRESENT.key());
            assertThat(run.executedActions()).containsExactly("runtime-api-test.finish");
        });
        assertThat(executions).hasValue(1);
    }

    @Test
    void canBeUsedThroughRuntimeOperationsInterface() {
        AtomicInteger executions = new AtomicInteger();
        ActionGraphRuntimeOperations operations = service(
                registry(new FinishAction(false, executions)),
                new AutoApproveHumanReviewPolicy(),
                null
        );

        RuntimeStartResponse response = operations.start("finish", Map.of("id", "I-ops"));

        assertThat(response.disposition()).isEqualTo(RuntimeStartDisposition.RUN_STARTED);
        assertThat(response.run()).hasValueSatisfying(run ->
                assertThat(run.status()).isEqualTo(RunStatus.COMPLETED));
        assertThat(executions).hasValue(1);
    }

    @Test
    void perItemBatchGoalInterpreterDelegatesToGoalInterpreterAndPreservesItemIds() {
        PerItemBatchGoalInterpreter interpreter = new PerItemBatchGoalInterpreter(new TestInterpreter());

        List<BatchGoalInterpretation> results = interpreter.interpret(List.of(
                BatchGoalInput.of("row-1", "finish", GoalParameters.of(Map.of("id", "I-1"))),
                BatchGoalInput.of("row-2", "finish")
        ));

        assertThat(results).extracting(BatchGoalInterpretation::itemId)
                .containsExactly("row-1", "row-2");
        assertThat(results.get(0).interpretation().isReady()).isTrue();
        assertThat(results.get(1).interpretation().isReady()).isFalse();
        assertThat(results.get(1).interpretation().missingFields())
                .containsExactly(new MissingField("id"));
    }

    @Test
    void batchGoalInputValidatesStableItemIdAndInput() {
        assertThatThrownBy(() -> BatchGoalInput.of(" ", "finish"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("itemId must not be blank");
        assertThatThrownBy(() -> BatchGoalInput.of("row-1", " "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input must not be blank");
    }

    @Test
    void startRecordsRunMetadataInRunStartedTrace() {
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        DefaultActionRegistry registry = registry(new FinishAction(false, new AtomicInteger()));
        ActionGraphRuntimeApiService service = service(registry, new AutoApproveHumanReviewPolicy(), null,
                traceRepository);

        RuntimeStartResponse response = service.start("finish", Map.of("id", "I-1"), Map.of(
                "requestHeader.X-Request-Id", "REQ-1",
                "requestHeader.X-Source-System", "legacy-crm"
        ));
        String runId = response.run().orElseThrow().runId();

        assertThat(traceRepository.findByRun(runId))
                .filteredOn(event -> event.type() == TraceEventType.RUN_STARTED)
                .singleElement()
                .satisfies(event -> assertThat(event.data())
                        .containsEntry("requestHeader.X-Request-Id", "REQ-1")
                        .containsEntry("requestHeader.X-Source-System", "legacy-crm")
                        .containsEntry("goal", "finish test workflow"));
    }

    @Test
    void returnsClarificationWithoutExecutingWhenRequiredParametersAreMissing() {
        AtomicInteger executions = new AtomicInteger();
        DefaultActionRegistry registry = registry(new FinishAction(false, executions));
        ActionGraphRuntimeApiService service = service(registry, new AutoApproveHumanReviewPolicy(), null);

        RuntimeStartResponse response = service.start("finish");

        assertThat(response.disposition()).isEqualTo(RuntimeStartDisposition.CLARIFICATION_REQUIRED);
        assertThat(response.run()).isEmpty();
        assertThat(response.interpretation().ready()).isFalse();
        assertThat(response.interpretation().missingFields()).containsExactly("id");
        assertThat(response.interpretation().clarificationQuestion()).contains("Which id should be processed?");
        assertThat(executions).hasValue(0);
    }

    @Test
    void resumeUsesTheSameRunIdAndRegistryActions() {
        DefaultActionRegistry registry = registry(new FinishAction(true, new AtomicInteger()));
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        ActionGraphRuntimeApiService service = service(registry, new PendingHumanReviewPolicy(), suspendedRuns);

        RuntimeStartResponse start = service.start("finish", Map.of("id", "I-1"));
        String runId = start.run().orElseThrow().runId();

        RuntimeRunResponse resumed = service.resume(runId);

        assertThat(start.run().orElseThrow().status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(resumed.runId()).isEqualTo(runId);
        assertThat(resumed.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(suspendedRuns.findByRunId(runId)).isPresent();
    }

    @Test
    void resumeRecordsRunMetadataInRunResumedTrace() {
        InMemoryTraceRepository traceRepository = new InMemoryTraceRepository();
        DefaultActionRegistry registry = registry(new FinishAction(true, new AtomicInteger()));
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        ActionGraphRuntimeApiService service = service(registry, new PendingHumanReviewPolicy(), suspendedRuns,
                traceRepository);
        String runId = service.start("finish", Map.of("id", "I-1")).run().orElseThrow().runId();

        service.resume(runId, Map.of("requestHeader.X-Request-Id", "REQ-RESUME-1"));

        assertThat(traceRepository.findByRun(runId))
                .filteredOn(event -> event.type() == TraceEventType.RUN_RESUMED)
                .singleElement()
                .satisfies(event -> assertThat(event.data())
                        .containsEntry("requestHeader.X-Request-Id", "REQ-RESUME-1")
                        .containsEntry("pendingActionId", "runtime-api-test.finish"));
    }

    @Test
    void validatesInputAndResumeRunId() {
        ActionGraphRuntimeApiService service = service(registry(new FinishAction(false, new AtomicInteger())),
                new AutoApproveHumanReviewPolicy(), null);

        assertThatThrownBy(() -> service.start(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input must not be blank");
        assertThatThrownBy(() -> service.resume(""))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId must not be blank");
    }

    private static ActionGraphRuntimeApiService service(
            DefaultActionRegistry registry,
            com.actiongraph.policy.HumanReviewPolicy humanReviewPolicy,
            InMemorySuspendedRunRepository suspendedRuns
    ) {
        return service(registry, humanReviewPolicy, suspendedRuns, new InMemoryTraceRepository());
    }

    private static ActionGraphRuntimeApiService service(
            DefaultActionRegistry registry,
            com.actiongraph.policy.HumanReviewPolicy humanReviewPolicy,
            InMemorySuspendedRunRepository suspendedRuns,
            InMemoryTraceRepository traceRepository
    ) {
        GoalBlackboardSeederRegistry seeders = new GoalBlackboardSeederRegistry();
        seeders.register(new TestSeeder());
        return new ActionGraphRuntimeApiService(
                new TestInterpreter(),
                seeders,
                new GoapExecutor(
                        new GoapPlanner(),
                        new DefaultPolicyGuard(),
                        humanReviewPolicy,
                        traceRepository,
                        suspendedRuns == null ? new InMemorySuspendedRunRepository() : suspendedRuns
                ),
                registry
        );
    }

    private static DefaultActionRegistry registry(Action action) {
        DefaultActionRegistry registry = new DefaultActionRegistry();
        registry.register(action);
        return registry;
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
                    new Goal("finish test workflow", Set.of(DONE))
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
            return new ActionId("runtime-api-test.finish");
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
