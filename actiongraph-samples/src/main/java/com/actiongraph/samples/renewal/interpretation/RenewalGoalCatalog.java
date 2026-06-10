package com.actiongraph.samples.renewal.interpretation;

import com.actiongraph.samples.renewal.RenewalGoals;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;

import java.util.List;

public final class RenewalGoalCatalog {
    private RenewalGoalCatalog() {
    }

    public static GoalCatalog create() {
        GoalCatalog catalog = new GoalCatalog();
        catalog.register(new GoalDefinition(
                RenewalGoalTypes.PREPARE_RENEWAL_QUOTE,
                "Prepare a renewal quote for an existing customer and request sales approval.",
                RenewalGoals.prepareRenewalQuote(),
                List.of(GoalParameterDefinition.required(
                        "customerId",
                        "Customer identifier. Use canonical IDs such as C001.",
                        "C001"
                ))
        ));
        return catalog;
    }
}
