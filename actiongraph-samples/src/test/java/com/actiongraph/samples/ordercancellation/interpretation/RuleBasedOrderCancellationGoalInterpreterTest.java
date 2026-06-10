package com.actiongraph.samples.ordercancellation.interpretation;

import com.actiongraph.samples.ordercancellation.OrderCancellationConditions;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.MissingField;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedOrderCancellationGoalInterpreterTest {
    private final RuleBasedOrderCancellationGoalInterpreter interpreter = new RuleBasedOrderCancellationGoalInterpreter();

    @Test
    void extractsOrderIdAndGoal() {
        var interpretation = interpreter.interpret("Cancel order O100");

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.goalType()).isEqualTo(OrderCancellationGoalTypes.REQUEST_ORDER_CANCELLATION);
        assertThat(interpretation.parameters().get("orderId")).contains("O100");
        assertThat(interpretation.goal()).get()
                .extracting(goal -> goal.targetConditions())
                .satisfies(conditions -> assertThat(conditions)
                        .containsExactly(OrderCancellationConditions.OPERATIONS_APPROVAL_REQUESTED));
    }

    @Test
    void usesKnownParametersForClarificationContinuation() {
        var interpretation = interpreter.interpret(
                "please proceed",
                GoalParameters.of(Map.of("orderId", "O200"))
        );

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.parameters().get("orderId")).contains("O200");
    }

    @Test
    void asksForMissingOrderId() {
        var interpretation = interpreter.interpret("Cancel that order");

        assertThat(interpretation.isReady()).isFalse();
        assertThat(interpretation.missingFields()).extracting(MissingField::name)
                .containsExactly("orderId");
        assertThat(interpretation.clarificationQuestion())
                .map(ClarificationQuestion::text)
                .contains("Which order id should I use for the cancellation request?");
    }
}
