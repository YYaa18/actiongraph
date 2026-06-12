package com.actiongraph.llm.evals;

import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoldenSetEvalRunnerTest {
    private static final GoalType TEST_GOAL = new GoalType("testGoal");
    private static final GoalType UNKNOWN = new GoalType("unknown");

    @TempDir
    Path tempDir;

    @Test
    void evaluatesExactStructuredFieldsAndWritesMarkdownReport() throws Exception {
        Path goldenSet = tempDir.resolve("golden.jsonl");
        Files.writeString(goldenSet, """
                {"input":"do T001","expect":{"goalType":"testGoal","parameters":{"id":"T001"}}}
                {"input":"do missing","expect":{"goalType":"testGoal","clarification":true,"missingFields":["id"]}}
                {"input":"weather","expect":{"unknownGoal":true,"clarification":true,"missingFields":["supportedGoal"]}}
                """);
        Path reportDir = tempDir.resolve("reports");

        EvalReport report = new GoldenSetEvalRunner(reportDir)
                .evaluate(new TestInterpreter(), goldenSet);

        assertThat(report.exact()).isTrue();
        assertThat(report.goalTypeAccuracy()).isEqualTo(1.0d);
        assertThat(report.parametersAccuracy()).isEqualTo(1.0d);
        assertThat(report.clarificationAccuracy()).isEqualTo(1.0d);
        assertThat(reportDir.resolve("golden.jsonl.md"))
                .exists()
                .content()
                .contains("ActionGraph Interpretation Eval Report");
    }

    @Test
    void caseDiffContainsInputExpectedAndActualTriplet() throws Exception {
        Path goldenSet = tempDir.resolve("broken.jsonl");
        Files.writeString(goldenSet, """
                {"input":"do T001","expect":{"goalType":"testGoal","parameters":{"id":"BROKEN"}}}
                """);

        EvalReport report = new GoldenSetEvalRunner(tempDir.resolve("reports"))
                .evaluate(new TestInterpreter(), goldenSet);

        assertThat(report.failures())
                .singleElement()
                .satisfies(diff -> {
                    assertThat(diff.input()).isEqualTo("do T001");
                    assertThat(diff.expected().parameters()).containsEntry("id", "BROKEN");
                    assertThat(diff.actual().parameters()).containsEntry("id", "T001");
                    assertThat(diff.differences()).anySatisfy(difference ->
                            assertThat(difference).contains("parameters expected"));
                });
        assertThat(report.toMarkdown())
                .contains("Expected")
                .contains("Actual")
                .contains("parameters expected");
    }

    @Test
    void assertionsFailWithMarkdownWhenThresholdIsNotMet() throws Exception {
        Path goldenSet = tempDir.resolve("threshold.jsonl");
        Files.writeString(goldenSet, """
                {"input":"do T001","expect":{"goalType":"testGoal","parameters":{"id":"BROKEN"}}}
                """);

        assertThatThrownBy(() -> GoldenSetAssertions.assertMeets(
                new TestInterpreter(),
                goldenSet,
                Thresholds.exact()
        ))
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("ActionGraph Interpretation Eval Report")
                .hasMessageContaining("do T001");
    }

    private static final class TestInterpreter implements GoalInterpreter {
        @Override
        public GoalInterpretation interpret(String input) {
            return interpret(input, GoalParameters.empty());
        }

        @Override
        public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
            if (input.contains("weather")) {
                return GoalInterpretation.needsClarification(
                        UNKNOWN,
                        GoalParameters.empty(),
                        Set.of(new MissingField("supportedGoal")),
                        new ClarificationQuestion("I can only interpret supported goals.")
                );
            }
            if (input.contains("missing")) {
                return GoalInterpretation.needsClarification(
                        TEST_GOAL,
                        GoalParameters.empty(),
                        Set.of(new MissingField("id")),
                        new ClarificationQuestion("Which id?")
                );
            }
            return GoalInterpretation.ready(
                    TEST_GOAL,
                    GoalParameters.of(Map.of("id", "T001")),
                    new Goal("testGoal", Set.of(Condition.of("test", "DONE")))
            );
        }
    }
}
