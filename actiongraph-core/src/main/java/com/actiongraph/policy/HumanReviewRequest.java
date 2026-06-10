package com.actiongraph.policy;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record HumanReviewRequest(
        String runId,
        ActionId actionId,
        ActionRiskLevel riskLevel,
        boolean requiredByAction,
        Plan planPreview,
        Set<Condition> currentState,
        Map<String, String> blackboardPreview,
        Map<String, String> attributes
) {
    public HumanReviewRequest(
            String runId,
            ActionId actionId,
            ActionRiskLevel riskLevel,
            boolean requiredByAction,
            Plan planPreview,
            Set<Condition> currentState,
            Map<String, String> blackboardPreview
    ) {
        this(runId, actionId, riskLevel, requiredByAction, planPreview, currentState, blackboardPreview, Map.of());
    }

    public HumanReviewRequest {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(riskLevel, "riskLevel");
        Objects.requireNonNull(planPreview, "planPreview");
        currentState = Set.copyOf(Objects.requireNonNull(currentState, "currentState"));
        blackboardPreview = Map.copyOf(Objects.requireNonNull(blackboardPreview, "blackboardPreview"));
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
    }
}
