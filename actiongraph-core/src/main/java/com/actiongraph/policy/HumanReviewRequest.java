package com.actiongraph.policy;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.identity.RunPrincipal;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.Plan;

import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Immutable review payload produced when policy or an action requires human
 * approval before execution.
 *
 * <p>The request contains a plan preview, current symbolic state, masked
 * Blackboard preview, and optional application attributes. It is intended for
 * display, audit, or external approval task creation. The receiving system
 * should treat {@link #runId()} and {@link #actionId()} as the correlation keys
 * used for resume callbacks.
 *
 * <p>Null contract: constructor arguments must be non-null except optional text
 * handled by nested value objects. Collection fields are defensively copied.
 *
 * @param runId stable run id used by trace, suspension, and callbacks
 * @param actionId pending action waiting for review
 * @param riskLevel action risk level at the time of review
 * @param requiredByAction whether the action itself declared review mandatory
 * @param planPreview current plan from this point forward
 * @param currentState current symbolic state snapshot
 * @param blackboardPreview masked Blackboard preview for reviewers
 * @param attributes masked application-specific review attributes
 */
public record HumanReviewRequest(
        String runId,
        ActionId actionId,
        ActionRiskLevel riskLevel,
        boolean requiredByAction,
        Plan planPreview,
        Set<Condition> currentState,
        Map<String, String> blackboardPreview,
        Map<String, String> attributes,
        RunPrincipal requestedBy
) {
    public HumanReviewRequest(
            String runId,
            ActionId actionId,
            ActionRiskLevel riskLevel,
            boolean requiredByAction,
            Plan planPreview,
            Set<Condition> currentState,
            Map<String, String> blackboardPreview,
            Map<String, String> attributes
    ) {
        this(runId, actionId, riskLevel, requiredByAction, planPreview, currentState, blackboardPreview,
                attributes, RunPrincipal.anonymous());
    }

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
        requestedBy = requestedBy == null ? RunPrincipal.anonymous() : requestedBy;
    }
}
