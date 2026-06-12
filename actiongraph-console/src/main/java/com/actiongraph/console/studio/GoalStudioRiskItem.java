package com.actiongraph.console.studio;

import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.api.Experimental;

@Experimental(
        since = "0.2.0",
        value = "Goal Studio risk previews are experimental until DX4 workflows settle."
)
public record GoalStudioRiskItem(
        String actionId,
        String description,
        ActionRiskLevel riskLevel,
        boolean requiresHumanReview,
        boolean missingDescription
) {
}
