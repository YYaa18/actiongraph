package com.actiongraph.samples.renewal.seed;

import com.actiongraph.samples.renewal.RenewalConditions;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.MissingGoalParameterException;
import com.actiongraph.runtime.InMemoryBlackboard;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenewalQuoteBlackboardSeederTest {
    @Test
    void seedsCustomerIdAndCondition() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        new RenewalQuoteBlackboardSeeder().seed(
                GoalParameters.of(Map.of("customerId", "C001")),
                blackboard
        );

        assertThat(blackboard.get(CustomerId.class)).contains(new CustomerId("C001"));
        assertThat(blackboard.conditions()).contains(RenewalConditions.CUSTOMER_ID_PRESENT);
    }

    @Test
    void missingCustomerIdThrows() {
        assertThatThrownBy(() -> new RenewalQuoteBlackboardSeeder().seed(
                GoalParameters.empty(),
                new InMemoryBlackboard()
        )).isInstanceOf(MissingGoalParameterException.class);
    }
}
