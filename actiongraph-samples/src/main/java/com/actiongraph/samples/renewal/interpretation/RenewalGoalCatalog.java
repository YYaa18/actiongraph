package com.actiongraph.samples.renewal.interpretation;

import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.samples.renewal.RenewalGoalAnnotations;

public final class RenewalGoalCatalog {
    private RenewalGoalCatalog() {
    }

    public static GoalCatalog create() {
        GoalCatalog catalog = new GoalCatalog();
        RenewalGoalAnnotations.goals().forEach(catalog::register);
        return catalog;
    }
}
