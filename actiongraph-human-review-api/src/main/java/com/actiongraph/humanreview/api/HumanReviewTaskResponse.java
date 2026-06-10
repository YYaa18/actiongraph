package com.actiongraph.humanreview.api;

import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.planning.Condition;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewTask;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public record HumanReviewTaskResponse(
        String runId,
        String actionId,
        ActionRiskLevel riskLevel,
        boolean requiredByAction,
        List<String> planPreview,
        List<String> currentState,
        Map<String, String> blackboardPreview,
        Map<String, String> attributes,
        HumanReviewDecision decision,
        String reviewer,
        String message,
        Instant createdAt,
        Instant updatedAt,
        List<HumanReviewStageResponse> stages,
        int currentStageIndex,
        String currentStageName,
        List<HumanReviewStageDecisionResponse> stageDecisions
) {
    public static HumanReviewTaskResponse from(HumanReviewTask task) {
        String currentStageName = task.decision() == HumanReviewDecision.PENDING
                ? task.currentStage().name()
                : "";
        return new HumanReviewTaskResponse(
                task.runId(),
                task.actionId().value(),
                task.riskLevel(),
                task.requiredByAction(),
                task.planPreview().stream().map(Object::toString).toList(),
                task.currentState().stream()
                        .map(Condition::key)
                        .sorted()
                        .toList(),
                task.blackboardPreview(),
                task.attributes(),
                task.decision(),
                task.reviewer(),
                task.message(),
                task.createdAt(),
                task.updatedAt(),
                task.stages().stream()
                        .map(HumanReviewStageResponse::from)
                        .toList(),
                task.currentStageIndex(),
                currentStageName,
                task.stageDecisions().stream()
                        .map(HumanReviewStageDecisionResponse::from)
                        .sorted(Comparator.comparing(HumanReviewStageDecisionResponse::at))
                        .toList()
        );
    }
}
