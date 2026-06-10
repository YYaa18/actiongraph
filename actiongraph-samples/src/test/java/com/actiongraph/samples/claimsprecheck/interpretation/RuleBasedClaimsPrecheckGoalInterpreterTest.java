package com.actiongraph.samples.claimsprecheck.interpretation;

import com.actiongraph.interpretation.GoalParameters;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RuleBasedClaimsPrecheckGoalInterpreterTest {
    @Test
    void extractsClaimIdFromInput() {
        var interpretation = new RuleBasedClaimsPrecheckGoalInterpreter()
                .interpret("帮我预审理赔 CLM100 并准备赔付申请");

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.parameters().get("claimId")).contains("CLM100");
        assertThat(interpretation.goal()).isPresent();
    }

    @Test
    void asksForClaimIdWhenMissing() {
        var interpretation = new RuleBasedClaimsPrecheckGoalInterpreter()
                .interpret("帮我做理赔预审");

        assertThat(interpretation.isReady()).isFalse();
        assertThat(interpretation.missingFields()).extracting(field -> field.name())
                .containsExactly("claimId");
        assertThat(interpretation.clarificationQuestion()).isPresent();
    }

    @Test
    void usesKnownClaimIdForClarificationFollowUp() {
        var interpretation = new RuleBasedClaimsPrecheckGoalInterpreter()
                .interpret("继续", GoalParameters.of(Map.of("claimId", "clm888")));

        assertThat(interpretation.isReady()).isTrue();
        assertThat(interpretation.parameters().get("claimId")).contains("CLM888");
    }
}
