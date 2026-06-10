package com.actiongraph.samples.renewal.interpretation;

import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.MissingField;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedRenewalGoalInterpreterTest {
    private final RuleBasedRenewalGoalInterpreter interpreter = new RuleBasedRenewalGoalInterpreter();

    @Test
    void interpretsRenewalQuoteInputIntoGoalAndParameters() {
        GoalInterpretation interpretation = interpreter.interpret("Prepare renewal quote for C001");

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.goalType()).isEqualTo(RenewalGoalTypes.PREPARE_RENEWAL_QUOTE);
        assertThat(interpretation.parameters().get("customerId")).contains("C001");
        assertThat(interpretation.goal())
                .get()
                .extracting(goal -> goal.targetConditions())
                .satisfies(conditions -> assertThat(conditions)
                        .containsExactly(RenewalConditions.SALES_APPROVAL_REQUESTED));
    }

    @Test
    void asksForMissingCustomerIdWithoutProducingGoal() {
        GoalInterpretation interpretation = interpreter.interpret("Prepare renewal quote");

        assertThat(interpretation.isReady()).isFalse();
        assertThat(interpretation.goal()).isEmpty();
        assertThat(interpretation.missingFields()).extracting(MissingField::name)
                .containsExactly("customerId");
        assertThat(interpretation.clarificationQuestion())
                .map(ClarificationQuestion::text)
                .contains("Which customer id should I use for the renewal quote?");
    }
}
