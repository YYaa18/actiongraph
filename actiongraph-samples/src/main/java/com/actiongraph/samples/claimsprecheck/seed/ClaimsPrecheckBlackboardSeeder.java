package com.actiongraph.samples.claimsprecheck.seed;

import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingGoalParameterException;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckConditions;
import com.actiongraph.samples.claimsprecheck.domain.ClaimId;
import com.actiongraph.samples.claimsprecheck.interpretation.ClaimsPrecheckGoalTypes;

public final class ClaimsPrecheckBlackboardSeeder implements GoalBlackboardSeeder {
    public static final String CLAIM_ID_PARAMETER = "claimId";

    @Override
    public GoalType goalType() {
        return ClaimsPrecheckGoalTypes.PREPARE_CLAIM_PAYOUT_APPLICATION;
    }

    @Override
    public void seed(GoalParameters parameters, Blackboard blackboard) {
        String claimId = parameters.get(CLAIM_ID_PARAMETER)
                .orElseThrow(() -> new MissingGoalParameterException(CLAIM_ID_PARAMETER));
        blackboard.put(new ClaimId(claimId));
        blackboard.addCondition(ClaimsPrecheckConditions.CLAIM_ID_PRESENT);
    }
}
