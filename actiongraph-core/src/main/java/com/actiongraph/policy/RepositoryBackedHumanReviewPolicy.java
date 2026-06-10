package com.actiongraph.policy;

import java.util.Objects;

public final class RepositoryBackedHumanReviewPolicy implements HumanReviewPolicy {
    private final HumanReviewRepository repository;

    public RepositoryBackedHumanReviewPolicy(HumanReviewRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return repository.find(request.runId(), request.actionId())
                .map(this::toResult)
                .orElseGet(() -> saveAndReturnPending(request));
    }

    private HumanReviewResult saveAndReturnPending(HumanReviewRequest request) {
        String message = "Human review is required before executing " + request.actionId().value();
        repository.savePending(HumanReviewTask.pending(request, message));
        return HumanReviewResult.pending(message);
    }

    private HumanReviewResult toResult(HumanReviewTask task) {
        return switch (task.decision()) {
            case APPROVED -> HumanReviewResult.approved(task.reviewer(), task.message());
            case DENIED -> HumanReviewResult.denied(task.reviewer(), task.message());
            case PENDING -> HumanReviewResult.pending(task.message());
        };
    }
}
