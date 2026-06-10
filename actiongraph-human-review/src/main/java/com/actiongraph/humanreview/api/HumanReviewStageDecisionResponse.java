package com.actiongraph.humanreview.api;

import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.StageDecision;

import java.time.Instant;

public record HumanReviewStageDecisionResponse(
        String stage,
        HumanReviewDecision decision,
        String reviewer,
        String comment,
        Instant at
) {
    public static HumanReviewStageDecisionResponse from(StageDecision decision) {
        return new HumanReviewStageDecisionResponse(
                decision.stage(),
                decision.decision(),
                decision.reviewer(),
                decision.comment(),
                decision.at()
        );
    }
}
