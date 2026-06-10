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
        Map<String, String> attributes,
        HumanReviewDecision decision,
        String reviewer,
        String message,
        Instant createdAt,
        Instant updatedAt,
        List<ApprovalStage> stages,
        int currentStageIndex,
        List<StageDecision> stageDecisions
) {
    public HumanReviewTask(
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
        this(runId, actionId, riskLevel, requiredByAction, planPreview, currentState, blackboardPreview,
                Map.of(), decision, reviewer, message, createdAt, updatedAt, ApprovalChain.single().stages(),
                defaultStageIndex(decision, ApprovalChain.single().stages()), List.of());
    }

    public HumanReviewTask {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        Objects.requireNonNull(actionId, "actionId");
        Objects.requireNonNull(riskLevel, "riskLevel");
        planPreview = List.copyOf(Objects.requireNonNull(planPreview, "planPreview"));
        currentState = Set.copyOf(Objects.requireNonNull(currentState, "currentState"));
        blackboardPreview = Map.copyOf(Objects.requireNonNull(blackboardPreview, "blackboardPreview"));
        attributes = Map.copyOf(Objects.requireNonNull(attributes, "attributes"));
        Objects.requireNonNull(decision, "decision");
        reviewer = reviewer == null ? "" : reviewer;
        message = message == null ? "" : message;
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        updatedAt = Objects.requireNonNull(updatedAt, "updatedAt");
        stages = List.copyOf(Objects.requireNonNull(stages, "stages"));
        if (stages.isEmpty()) {
            throw new IllegalArgumentException("human review task must contain at least one approval stage");
        }
        if (currentStageIndex < 0 || currentStageIndex > stages.size()) {
            throw new IllegalArgumentException("currentStageIndex must be between 0 and stages.size()");
        }
        if (decision == HumanReviewDecision.PENDING && currentStageIndex >= stages.size()) {
            throw new IllegalArgumentException("pending human review task must point to an unfinished stage");
        }
        if (decision == HumanReviewDecision.APPROVED) {
            currentStageIndex = stages.size();
        }
        stageDecisions = List.copyOf(Objects.requireNonNull(stageDecisions, "stageDecisions"));
    }

    public static HumanReviewTask pending(HumanReviewRequest request, String message) {
        return pending(request, message, ApprovalChain.single());
    }

    public static HumanReviewTask pending(HumanReviewRequest request, String message, ApprovalChain chain) {
        Instant now = Instant.now();
        ApprovalChain safeChain = Objects.requireNonNull(chain, "chain");
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
                request.attributes(),
                HumanReviewDecision.PENDING,
                "",
                message,
                now,
                now,
                safeChain.stages(),
                0,
                List.of()
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
        if (decision != HumanReviewDecision.PENDING || currentStageIndex >= stages.size()) {
            throw new StageAlreadyDecidedException(runId, actionId, currentStageIndex);
        }
        StageDecision stageDecision = new StageDecision(
                stages.get(currentStageIndex).name(),
                newDecision,
                newReviewer,
                newMessage,
                newUpdatedAt
        );
        List<StageDecision> updatedStageDecisions = new java.util.ArrayList<>(stageDecisions);
        updatedStageDecisions.add(stageDecision);
        int nextStageIndex = currentStageIndex;
        HumanReviewDecision taskDecision = newDecision;
        if (newDecision == HumanReviewDecision.APPROVED) {
            nextStageIndex = currentStageIndex + 1;
            taskDecision = nextStageIndex >= stages.size()
                    ? HumanReviewDecision.APPROVED
                    : HumanReviewDecision.PENDING;
        }
        return new HumanReviewTask(
                runId,
                actionId,
                riskLevel,
                requiredByAction,
                planPreview,
                currentState,
                blackboardPreview,
                attributes,
                taskDecision,
                newReviewer,
                newMessage,
                createdAt,
                newUpdatedAt,
                stages,
                nextStageIndex,
                updatedStageDecisions
        );
    }

    public ApprovalStage currentStage() {
        if (currentStageIndex >= stages.size()) {
            throw new IllegalStateException("Human review task has no pending stage");
        }
        return stages.get(currentStageIndex);
    }

    private static int defaultStageIndex(HumanReviewDecision decision, List<ApprovalStage> stages) {
        return decision == HumanReviewDecision.APPROVED ? stages.size() : 0;
    }
}
