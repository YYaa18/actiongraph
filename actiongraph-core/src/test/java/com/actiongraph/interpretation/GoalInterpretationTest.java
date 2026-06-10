package com.actiongraph.interpretation;

import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Goal;
import com.actiongraph.planning.Plan;
import org.junit.jupiter.api.Test;

import java.lang.reflect.RecordComponent;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class GoalInterpretationTest {
    @Test
    void readyInterpretationContainsGoalButNoPlan() {
        Goal goal = new Goal("done", Set.of(Condition.of("DONE")));

        GoalInterpretation interpretation = GoalInterpretation.ready(
                new GoalType("done"),
                GoalParameters.of(Map.of("customerId", "C001")),
                goal
        );

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.goal()).contains(goal);
        assertThat(interpretation.parameters().get("customerId")).contains("C001");
        assertThat(interpretation.missingFields()).isEmpty();
        assertThat(interpretation.clarificationQuestion()).isEmpty();
    }

    @Test
    void clarificationInterpretationDoesNotContainGoal() {
        GoalInterpretation interpretation = GoalInterpretation.needsClarification(
                new GoalType("prepareRenewalQuote"),
                GoalParameters.empty(),
                Set.of(new MissingField("customerId")),
                new ClarificationQuestion("Which customer id should I use?")
        );

        assertThat(interpretation.isReady()).isFalse();
        assertThat(interpretation.goal()).isEmpty();
        assertThat(interpretation.missingFields()).extracting(MissingField::name)
                .containsExactly("customerId");
        assertThat(interpretation.clarificationQuestion())
                .map(ClarificationQuestion::text)
                .contains("Which customer id should I use?");
    }

    @Test
    void interpretationRecordDoesNotExposeAPlanBoundary() {
        assertThat(GoalInterpretation.class.getRecordComponents())
                .extracting(RecordComponent::getType)
                .doesNotContain(Plan.class);
        assertThat(GoalInterpretation.class.getDeclaredMethods())
                .noneMatch(method -> method.getReturnType().equals(Plan.class));
    }
}
