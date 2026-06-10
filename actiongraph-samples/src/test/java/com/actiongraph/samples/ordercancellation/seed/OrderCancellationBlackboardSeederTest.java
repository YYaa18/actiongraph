package com.actiongraph.samples.ordercancellation.seed;

import com.actiongraph.samples.ordercancellation.OrderCancellationConditions;
import com.actiongraph.samples.ordercancellation.domain.OrderId;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.MissingGoalParameterException;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OrderCancellationBlackboardSeederTest {
    @Test
    void seedsOrderIdAndCondition() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        new OrderCancellationBlackboardSeeder().seed(
                GoalParameters.of(Map.of("orderId", "O100")),
                blackboard
        );

        assertThat(blackboard.get(OrderId.class)).contains(new OrderId("O100"));
        assertThat(blackboard.conditions()).contains(OrderCancellationConditions.ORDER_ID_PRESENT);
    }

    @Test
    void missingOrderIdThrows() {
        assertThatThrownBy(() -> new OrderCancellationBlackboardSeeder().seed(
                GoalParameters.empty(),
                new InMemoryBlackboard()
        )).isInstanceOf(MissingGoalParameterException.class);
    }
}
