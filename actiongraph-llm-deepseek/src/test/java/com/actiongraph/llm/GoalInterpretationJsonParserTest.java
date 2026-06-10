package com.actiongraph.llm;

import com.actiongraph.interpretation.GoalType;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GoalInterpretationJsonParserTest {
    private static final GoalType TEST_GOAL_TYPE = new GoalType("testGoal");
    private static final Condition DONE = Condition.of("test", "DONE");

    private final GoalInterpretationJsonParser parser = new GoalInterpretationJsonParser(Map.of(
            TEST_GOAL_TYPE,
            new Goal("testGoal", Set.of(DONE))
    ));

    @Test
    void parsesReadyInterpretationFromJsonFence() {
        String text = """
                ```json
                {
                  "goalType": "testGoal",
                  "parameters": {"id": "T001"},
                  "missingFields": [],
                  "clarificationQuestion": null
                }
                ```
                """;

        var interpretation = parser.parse(text);

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.parameters().get("id")).contains("T001");
        assertThat(interpretation.goal()).get()
                .extracting(goal -> goal.targetConditions())
                .satisfies(conditions -> assertThat(conditions)
                        .containsExactly(DONE));
    }

    @Test
    void parsesMissingFieldInterpretation() {
        String text = """
                {
                  "goalType": "testGoal",
                  "parameters": {},
                  "missingFields": ["id"],
                  "clarificationQuestion": "Which id should I use?"
                }
                """;

        var interpretation = parser.parse(text);

        assertThat(interpretation.isReady()).isFalse();
        assertThat(interpretation.goal()).isEmpty();
        assertThat(interpretation.clarificationQuestion())
                .get()
                .extracting(question -> question.text())
                .isEqualTo("Which id should I use?");
    }

    @Test
    void rejectsMalformedOutput() {
        assertThatThrownBy(() -> parser.parse("not json"))
                .isInstanceOf(StructuredOutputException.class);
    }
}
