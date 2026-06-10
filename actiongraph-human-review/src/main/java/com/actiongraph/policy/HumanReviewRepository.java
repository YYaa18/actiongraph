package com.actiongraph.policy;

import com.actiongraph.action.ActionId;

import java.util.List;
import java.util.Optional;

public interface HumanReviewRepository {
    void savePending(HumanReviewTask task);

    Optional<HumanReviewTask> find(String runId, ActionId actionId);

    List<HumanReviewTask> findByRun(String runId);

    List<HumanReviewTask> findPending();

    void decide(
            String runId,
            ActionId actionId,
            HumanReviewDecision decision,
            String reviewer,
            String message
    );

    default void decideStage(
            String runId,
            ActionId actionId,
            int expectedStageIndex,
            HumanReviewDecision decision,
            String reviewer,
            String message
    ) {
        decide(runId, actionId, decision, reviewer, message);
    }
}
