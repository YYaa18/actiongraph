package com.actiongraph.console.studio;

import com.actiongraph.api.Experimental;
import com.actiongraph.interpretation.config.ConfiguredGoalDefinition;

import java.util.List;

import org.jspecify.annotations.Nullable;

@Experimental(
        since = "0.2.0",
        value = "Goal Studio session responses are experimental until DX4 workflows settle."
)
public record GoalStudioSessionResponse(
        String id,
        GoalStudioStatus status,
        String description,
        @Nullable ConfiguredGoalDefinition draft,
        boolean reachable,
        List<String> diagnostics,
        List<String> previewPlan,
        List<GoalStudioRiskItem> riskProfile,
        int repairAttempts,
        @Nullable String bundlePath,
        @Nullable String bundle
) {
    public GoalStudioSessionResponse {
        diagnostics = List.copyOf(diagnostics);
        previewPlan = List.copyOf(previewPlan);
        riskProfile = List.copyOf(riskProfile);
    }
}
