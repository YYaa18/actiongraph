package com.actiongraph.planning;

import com.actiongraph.action.ActionId;

import java.util.Objects;

public record PlanStep(ActionId actionId) {
    public PlanStep {
        Objects.requireNonNull(actionId, "actionId");
    }
}
