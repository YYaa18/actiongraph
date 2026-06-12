package com.actiongraph.console.studio;

import com.actiongraph.api.Experimental;

@Experimental(
        since = "0.2.0",
        value = "Goal Studio sessions are experimental until DX4 test-environment workflows settle."
)
public enum GoalStudioStatus {
    DRAFT_VALID,
    DRAFT_INVALID,
    APPROVED
}
