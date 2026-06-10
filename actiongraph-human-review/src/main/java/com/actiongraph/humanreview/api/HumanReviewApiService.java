package com.actiongraph.humanreview.api;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRepository;

import java.util.List;
import java.util.Objects;

public final class HumanReviewApiService {
    private final HumanReviewRepository repository;

    public HumanReviewApiService(HumanReviewRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public List<HumanReviewTaskResponse> pendingTasks() {
        return repository.findPending().stream()
                .map(HumanReviewTaskResponse::from)
                .toList();
    }

    public List<HumanReviewTaskResponse> tasksForRun(String runId) {
        requireRunId(runId);
        return repository.findByRun(runId).stream()
                .map(HumanReviewTaskResponse::from)
                .toList();
    }

    public HumanReviewTaskResponse task(String runId, String actionId) {
        ActionId parsedActionId = actionId(actionId);
        return repository.find(requireRunId(runId), parsedActionId)
                .map(HumanReviewTaskResponse::from)
                .orElseThrow(() -> new HumanReviewTaskNotFoundException(runId, parsedActionId));
    }

    public HumanReviewTaskResponse decide(
            String runId,
            String actionId,
            Integer expectedStageIndex,
            HumanReviewDecision decision,
            String reviewer,
            String message
    ) {
        String safeRunId = requireRunId(runId);
        ActionId parsedActionId = actionId(actionId);
        Objects.requireNonNull(decision, "decision");
        if (decision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("Human review task decision must be APPROVED or DENIED");
        }
        if (repository.find(safeRunId, parsedActionId).isEmpty()) {
            throw new HumanReviewTaskNotFoundException(safeRunId, parsedActionId);
        }
        if (expectedStageIndex == null) {
            repository.decide(safeRunId, parsedActionId, decision, reviewer, message);
        } else {
            repository.decideStage(safeRunId, parsedActionId, expectedStageIndex, decision, reviewer, message);
        }
        return task(safeRunId, parsedActionId.value());
    }

    private static String requireRunId(String runId) {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        return runId;
    }

    private static ActionId actionId(String actionId) {
        return new ActionId(actionId);
    }
}
