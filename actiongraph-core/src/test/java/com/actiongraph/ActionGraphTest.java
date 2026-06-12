package com.actiongraph;

import com.actiongraph.action.ActionResult;
import com.actiongraph.action.annotation.ActionGraphAction;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.interpretation.annotation.ActionGraphGoal;
import com.actiongraph.interpretation.annotation.ActionGraphGoalSeeder;
import com.actiongraph.interpretation.annotation.FromGoalParam;
import com.actiongraph.interpretation.annotation.GoalParameter;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.policy.HumanReviewPolicy;
import com.actiongraph.policy.HumanReviewRequest;
import com.actiongraph.policy.HumanReviewResult;
import com.actiongraph.runtime.GoapExecutor;
import com.actiongraph.runtime.InMemorySuspendedRunRepository;
import com.actiongraph.runtime.RunResult;
import com.actiongraph.runtime.RunStatus;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphTest {
    private static final GoalType TYPE = new GoalType("hello.finish");
    private static final Condition DONE = Condition.of("hello:DONE");

    @Test
    void builderRunsAnnotatedWorkflowWithoutSpring() {
        HelloWorkflow workflow = new HelloWorkflow();
        ActionGraph actionGraph = ActionGraph.builder()
                .annotatedBeans(workflow)
                .build();

        RunResult result = actionGraph.start("hello.finish", Map.of("id", "A-1"));

        assertThat(result.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(result.finalState()).contains(DONE);
        assertThat(workflow.executions).hasValue(1);
    }

    @Test
    void startReportsUnknownGoalTypeAndMissingParametersAsCallerInput() {
        ActionGraph actionGraph = ActionGraph.builder()
                .annotatedBeans(new HelloWorkflow())
                .build();

        assertThatThrownBy(() -> actionGraph.start("missing.goal", Map.of()))
                .isInstanceOf(ActionGraphInputException.class)
                .hasMessageContaining("Unknown goalType: missing.goal")
                .hasMessageContaining("hello.finish");
        assertThatThrownBy(() -> actionGraph.start("hello.finish", Map.of()))
                .isInstanceOf(ActionGraphInputException.class)
                .hasMessageContaining("Missing required goal parameter(s): id");
    }

    @Test
    void chatClarifiesMissingParametersAndStartsWhenKnownParametersCompleteThem() {
        HelloWorkflow workflow = new HelloWorkflow();
        ActionGraph actionGraph = ActionGraph.builder()
                .annotatedBeans(workflow)
                .goalInterpreter(new HelloInterpreter())
                .build();

        ChatResult missing = actionGraph.chat("finish it");
        ChatResult started = actionGraph.chat("finish it", Map.of("id", "A-2"));

        assertThat(missing.started()).isFalse();
        assertThat(missing.run()).isNull();
        assertThat(missing.clarification().text()).contains("id");
        assertThat(started.started()).isTrue();
        assertThat(started.run()).extracting(RunResult::status).isEqualTo(RunStatus.COMPLETED);
        assertThat(workflow.executions).hasValue(1);
    }

    @Test
    void chatRequiresGoalInterpreter() {
        ActionGraph actionGraph = ActionGraph.builder()
                .annotatedBeans(new HelloWorkflow())
                .build();

        assertThatThrownBy(() -> actionGraph.chat("finish it"))
                .isInstanceOf(ActionGraphConfigurationException.class)
                .hasMessageContaining("No GoalInterpreter configured");
    }

    @Test
    void resumeContinuesSuspendedRunThroughFacade() {
        ToggleReviewPolicy reviewPolicy = new ToggleReviewPolicy();
        InMemorySuspendedRunRepository suspendedRuns = new InMemorySuspendedRunRepository();
        ActionGraph actionGraph = ActionGraph.builder()
                .annotatedBeans(new ReviewWorkflow())
                .executor(GoapExecutor.builder()
                        .humanReviewPolicy(reviewPolicy)
                        .suspendedRunRepository(suspendedRuns)
                        .build())
                .build();

        RunResult suspended = actionGraph.start("hello.finish", Map.of("id", "A-3"));
        reviewPolicy.approve.set(true);
        RunResult resumed = actionGraph.resume(suspended.runId());

        assertThat(suspended.status()).isEqualTo(RunStatus.SUSPENDED_PENDING_REVIEW);
        assertThat(resumed.runId()).isEqualTo(suspended.runId());
        assertThat(resumed.status()).isEqualTo(RunStatus.COMPLETED);
        assertThat(suspendedRuns.findByRunId(suspended.runId())).isEmpty();
    }

    static class HelloWorkflow {
        final AtomicInteger executions = new AtomicInteger();

        @ActionGraphGoal(
                type = "hello.finish",
                name = "finish hello",
                description = "Finish a hello workflow.",
                targetConditions = "hello:DONE",
                seedConditions = "hello:INPUT_PRESENT"
        )
        void goal(@GoalParameter(name = "id", description = "Input id", example = "A-1") String ignored) {
        }

        @ActionGraphGoalSeeder(value = "hello.finish", seedConditions = "hello:INPUT_PRESENT")
        InputId seed(@FromGoalParam("id") String id) {
            return new InputId(id);
        }

        @ActionGraphAction(
                id = "hello.finish",
                preconditions = "hello:INPUT_PRESENT",
                effects = "hello:DONE"
        )
        ActionResult finish(InputId input) {
            executions.incrementAndGet();
            return ActionResult.ok();
        }
    }

    static final class ReviewWorkflow {
        @ActionGraphGoal(
                type = "hello.finish",
                name = "finish hello",
                description = "Finish a hello workflow.",
                targetConditions = "hello:DONE",
                seedConditions = "hello:INPUT_PRESENT"
        )
        void goal(@GoalParameter(name = "id", description = "Input id", example = "A-1") String ignored) {
        }

        @ActionGraphGoalSeeder(value = "hello.finish", seedConditions = "hello:INPUT_PRESENT")
        InputId seed(@FromGoalParam("id") String id) {
            return new InputId(id);
        }

        @ActionGraphAction(
                id = "hello.finish",
                preconditions = "hello:INPUT_PRESENT",
                effects = "hello:DONE",
                requiresHumanReview = true
        )
        ActionResult finish(InputId input) {
            return ActionResult.ok();
        }
    }

    private static final class HelloInterpreter implements GoalInterpreter {
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
                        new ClarificationQuestion("Which id should be finished?")
                );
            }
            return GoalInterpretation.ready(TYPE, knownParameters, new Goal("finish hello", Set.of(DONE)));
        }
    }

    private static final class ToggleReviewPolicy implements HumanReviewPolicy {
        private final AtomicBoolean approve = new AtomicBoolean(false);

        @Override
        public HumanReviewResult review(HumanReviewRequest request) {
            if (approve.get()) {
                return HumanReviewResult.approved("approver", "approved");
            }
            return HumanReviewResult.pending("pending");
        }
    }

    record InputId(String value) {
    }
}
