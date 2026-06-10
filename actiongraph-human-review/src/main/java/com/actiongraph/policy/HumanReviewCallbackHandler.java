package com.actiongraph.policy;

import java.util.Objects;

public final class HumanReviewCallbackHandler {
    private final HumanReviewRepository repository;

    public HumanReviewCallbackHandler(HumanReviewRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository");
    }

    public HumanReviewTask handle(HumanReviewCallback callback) {
        Objects.requireNonNull(callback, "callback");
        repository.decideStage(
                callback.runId(),
                callback.actionId(),
                callback.expectedStageIndex(),
                callback.decision(),
                callback.reviewer(),
                callback.comment()
        );
        return repository.find(callback.runId(), callback.actionId())
                .orElseThrow(() -> new IllegalStateException("No human review task found after callback for "
                        + callback.runId() + "/" + callback.actionId().value()));
    }
}
