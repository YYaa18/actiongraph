package com.actiongraph.samples.claimsprecheck;

import com.actiongraph.planning.Goal;

import java.util.Set;

public final class ClaimsPrecheckGoals {
    private ClaimsPrecheckGoals() {
    }

    public static Goal prepareClaimPayoutApplication() {
        return new Goal("prepareClaimPayoutApplication", Set.of(ClaimsPrecheckConditions.CLAIM_APPROVAL_REQUESTED));
    }
}
