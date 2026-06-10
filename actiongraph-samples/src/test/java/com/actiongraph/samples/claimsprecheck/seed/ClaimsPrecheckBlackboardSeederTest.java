package com.actiongraph.samples.claimsprecheck.seed;

import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.MissingGoalParameterException;
import com.actiongraph.runtime.InMemoryBlackboard;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.domain.ClaimId;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ClaimsPrecheckBlackboardSeederTest {
    @Test
    void seedsClaimIdAndCondition() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();

        new ClaimsPrecheckBlackboardSeeder().seed(
                GoalParameters.of(Map.of("claimId", "clm100")),
                blackboard
        );

        assertThat(blackboard.get(ClaimId.class)).contains(new ClaimId("CLM100"));
        assertThat(blackboard.conditions()).contains(ClaimsPrecheckConditions.CLAIM_ID_PRESENT);
    }

    @Test
    void failsWhenClaimIdIsMissing() {
        assertThatThrownBy(() -> new ClaimsPrecheckBlackboardSeeder()
                .seed(GoalParameters.empty(), new InMemoryBlackboard()))
                .isInstanceOf(MissingGoalParameterException.class)
                .hasMessageContaining("claimId");
    }
}
