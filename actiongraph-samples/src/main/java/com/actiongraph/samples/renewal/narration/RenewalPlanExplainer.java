package com.actiongraph.samples.renewal.narration;

import com.actiongraph.planning.Plan;

import java.util.List;

public final class RenewalPlanExplainer {
    public String explain(Plan plan) {
        if (plan.isEmpty()) {
            return "Plan: goal is already satisfied.";
        }
        List<String> steps = plan.steps().stream()
                .map(step -> step.actionId().value())
                .toList();
        return "Plan: " + String.join(" -> ", steps);
    }
}
