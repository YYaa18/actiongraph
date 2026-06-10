package com.actiongraph.samples.renewal;

import com.actiongraph.planning.Goal;

import java.util.Set;

public final class RenewalGoals {
    private RenewalGoals() {
    }

    public static Goal prepareRenewalQuote() {
        return new Goal("prepareRenewalQuote", Set.of(RenewalConditions.SALES_APPROVAL_REQUESTED));
    }
}
