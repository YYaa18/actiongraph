package com.actiongraph.llm;

import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class LlmGoalInterpreterTest {
    private static final GoalType TEST_GOAL_TYPE = new GoalType("testGoal");
    private static final Condition DONE = Condition.of("test", "DONE");

    @Test
    void interpretsStructuredLlmOutput() {
        LlmGoalInterpreter interpreter = new LlmGoalInterpreter(
                request -> new LlmResponse("""
                        {
                          "goalType": "testGoal",
                          "parameters": {"id": "T001"},
                          "missingFields": [],
                          "clarificationQuestion": null
                        }
                        """),
                (input, knownParameters) -> new LlmRequest("system", input, 200),
                parser()
        );

        var interpretation = interpreter.interpret("please do test goal for T001");

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.parameters().get("id")).contains("T001");
        assertThat(interpretation.goalType()).isEqualTo(TEST_GOAL_TYPE);
    }

    @Test
    void malformedLlmOutputReturnsClarification() {
        LlmGoalInterpreter interpreter = new LlmGoalInterpreter(
                request -> new LlmResponse("I am not JSON"),
                (input, knownParameters) -> new LlmRequest("system", input, 200),
                parser()
        );

        var interpretation = interpreter.interpret("renewal quote");

        assertThat(interpretation.isReady()).isFalse();
        assertThat(interpretation.missingFields())
                .extracting(field -> field.name())
                .containsExactly("validGoalInterpretation");
    }

    @Test
    void llmClientFailureFallsBackToRuleBasedInterpreter() {
        FallbackGoalInterpreter interpreter = new FallbackGoalInterpreter(
                new GoalInterpreter() {
                    @Override
                    public GoalInterpretation interpret(String input) {
                        throw new LlmClientException("network down");
                    }

                    @Override
                    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
                        throw new LlmClientException("network down");
                    }
                },
                new GoalInterpreter() {
                    @Override
                    public GoalInterpretation interpret(String input) {
                        return interpret(input, GoalParameters.empty());
                    }

                    @Override
                    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
                        GoalParameters parameters = knownParameters.merge(GoalParameters.of(Map.of("id", "T009")));
                        return GoalInterpretation.ready(TEST_GOAL_TYPE, parameters, new Goal("testGoal", Set.of(DONE)));
                    }
                }
        );

        var interpretation = interpreter.interpret("please do test goal", GoalParameters.empty());

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.parameters().get("id")).contains("T009");
    }

    private GoalInterpretationJsonParser parser() {
        return new GoalInterpretationJsonParser(Map.of(
                TEST_GOAL_TYPE,
                new Goal("testGoal", Set.of(DONE))
        ));
    }
}
