package com.actiongraph.policy;

import java.util.Objects;
import java.util.stream.Collectors;

public final class RepositoryBackedHumanReviewPolicy implements HumanReviewPolicy {
    private final HumanReviewRepository repository;
    private final ApprovalChainResolver chainResolver;

    public RepositoryBackedHumanReviewPolicy(HumanReviewRepository repository) {
        this(repository, SingleStageApprovalChainResolver.INSTANCE);
    }

    public RepositoryBackedHumanReviewPolicy(
            HumanReviewRepository repository,
            ApprovalChainResolver chainResolver
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.chainResolver = Objects.requireNonNull(chainResolver, "chainResolver");
    }

    @Override
    public HumanReviewResult review(HumanReviewRequest request) {
        return repository.find(request.runId(), request.actionId())
                .map(this::toResult)
                .orElseGet(() -> saveAndReturnPending(request));
    }

    private HumanReviewResult saveAndReturnPending(HumanReviewRequest request) {
        String message = "Human review is required before executing " + request.actionId().value();
        repository.savePending(HumanReviewTask.pending(request, message, chainResolver.resolve(request)));
        return HumanReviewResult.pending(message);
    }

    private HumanReviewResult toResult(HumanReviewTask task) {
        return switch (task.decision()) {
            case APPROVED -> HumanReviewResult.approved(task.reviewer(), stageSummary(task));
            case DENIED -> HumanReviewResult.denied(task.reviewer(), stageSummary(task));
            case PENDING -> HumanReviewResult.pending(task.message());
        };
    }

    private String stageSummary(HumanReviewTask task) {
        if (task.stageDecisions().size() <= 1) {
            return task.message();
        }
        return task.stageDecisions().stream()
                .map(decision -> decision.stage() + "=" + decision.decision().name()
                        + " by " + decision.reviewer() + ": " + decision.comment())
                .collect(Collectors.joining("; "));
    }
}
