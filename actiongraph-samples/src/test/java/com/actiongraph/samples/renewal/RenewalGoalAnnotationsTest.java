package com.actiongraph.samples.renewal;

import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.samples.renewal.domain.CustomerId;
import com.actiongraph.samples.renewal.interpretation.RenewalGoalTypes;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RenewalGoalAnnotationsTest {
    @Test
    void annotatedSeederSeedsCustomerIdAndCondition() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        RenewalGoalAnnotations.seeders().getFirst().seed(
                GoalParameters.of(Map.of("customerId", "C001")),
                blackboard
        );

        assertThat(blackboard.get(CustomerId.class)).contains(new CustomerId("C001"));
        assertThat(blackboard.conditions()).contains(RenewalConditions.CUSTOMER_ID_PRESENT);
    }

    @Test
    void annotatedMetadataPublishesRenewalGoalDefinition() {
        var goal = RenewalGoalAnnotations.goals().getFirst();

        assertThat(goal.type()).isEqualTo(RenewalGoalTypes.PREPARE_RENEWAL_QUOTE);
        assertThat(goal.seedConditions()).containsExactly(RenewalConditions.CUSTOMER_ID_PRESENT);
        assertThat(goal.goal().targetConditions()).containsExactly(RenewalConditions.SALES_APPROVAL_REQUESTED);
    }

    @Test
    void missingCustomerIdThrowsInputException() {
        assertThatThrownBy(() -> RenewalGoalAnnotations.seeders().getFirst().seed(
                GoalParameters.empty(),
                new InMemoryBlackboard()
        )).isInstanceOf(ActionGraphInputException.class)
                .hasMessageContaining("customerId");
    }
}
