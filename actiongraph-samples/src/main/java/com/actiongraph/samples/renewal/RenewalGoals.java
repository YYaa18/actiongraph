package com.actiongraph.samples.renewal;

import com.actiongraph.planning.Goal;

public final class RenewalGoals {
    private RenewalGoals() {
    }

    public static Goal prepareRenewalQuote() {
        return RenewalGoalAnnotations.prepareRenewalQuoteGoal();
    }
}
