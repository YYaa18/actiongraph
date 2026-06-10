package com.actiongraph.policy;

import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.planning.PlanStep;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public record HumanReviewTask(
        String runId,
        ActionId actionId,
        ActionRiskLevel riskLevel,
        boolean requiredByAction,
        List<ActionId> planPreview,
        Set<Condition> currentState,
        Map<String, String> blackboardPreview,
        HumanReviewDecision decision,
        String reviewer,
        String message,
        Instant createdAt,
        Instant updatedAt
) {
    public HumanReviewTask {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(riskLevel, "riskLevel");
        planPreview = List.copyOf(Objects.requireNonNull(planPreview, "planPreview"));
        currentState = Set.copyOf(Objects.requireNonNull(currentState, "currentState"));
        blackboardPreview = Map.copyOf(Objects.requireNonNull(blackboardPreview, "blackboardPreview"));
        Objects.requireNonNull(decision, "decision");
        reviewer = reviewer == null ? "" : reviewer;
        message = message == null ? "" : message;
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
    }

    public static HumanReviewTask pending(HumanReviewRequest request, String message) {
        Instant now = Instant.now();
        return new HumanReviewTask(
                request.runId(),
                request.actionId(),
                request.riskLevel(),
                request.requiredByAction(),
                request.planPreview().steps().stream()
                        .map(PlanStep::actionId)
                        .toList(),
                request.currentState(),
                request.blackboardPreview(),
                HumanReviewDecision.PENDING,
                "",
                message,
                now,
                now
        );
    }

    public HumanReviewTask withDecision(
            HumanReviewDecision newDecision,
            String newReviewer,
            String newMessage,
            Instant newUpdatedAt
    ) {
        if (newDecision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("Human review task decision must be APPROVED or DENIED");
        }
        return new HumanReviewTask(
                runId,
                actionId,
                riskLevel,
                requiredByAction,
                planPreview,
                currentState,
                blackboardPreview,
                newDecision,
                newReviewer,
                newMessage,
                createdAt,
                newUpdatedAt
        );
    }
}
