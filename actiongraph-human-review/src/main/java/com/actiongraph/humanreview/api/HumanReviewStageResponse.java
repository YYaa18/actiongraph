package com.actiongraph.humanreview.api;

import com.actiongraph.policy.ApprovalStage;

public record HumanReviewStageResponse(
        String name,
        String requiredRole
) {
    public static HumanReviewStageResponse from(ApprovalStage stage) {
        return new HumanReviewStageResponse(stage.name(), stage.requiredRole());
    }
}
