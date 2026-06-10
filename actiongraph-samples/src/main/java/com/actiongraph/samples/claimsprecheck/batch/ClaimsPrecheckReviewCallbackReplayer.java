package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.policy.HumanReviewCallbackHandler;
import com.actiongraph.policy.HumanReviewDecision;
import com.actiongraph.policy.HumanReviewRepository;
import com.actiongraph.policy.HumanReviewTask;
import com.actiongraph.policy.StageAlreadyDecidedException;
import com.actiongraph.policy.StageDecision;

import java.util.List;
import java.util.Objects;

public final class ClaimsPrecheckReviewCallbackReplayer {
    private final HumanReviewRepository repository;
    private final HumanReviewCallbackHandler callbackHandler;
    private final List<ClaimsPrecheckBatchReviewCallback> callbacks;
    private final String sharedSecret;

    public ClaimsPrecheckReviewCallbackReplayer(
            HumanReviewRepository repository,
            HumanReviewCallbackHandler callbackHandler,
            List<ClaimsPrecheckBatchReviewCallback> callbacks,
            String sharedSecret
    ) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.callbackHandler = Objects.requireNonNull(callbackHandler, "callbackHandler");
        this.callbacks = List.copyOf(Objects.requireNonNull(callbacks, "callbacks"));
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public HumanReviewTask apply(String claimId, HumanReviewTask pendingTask) {
        Objects.requireNonNull(pendingTask, "pendingTask");
        List<ClaimsPrecheckBatchReviewCallback> matches = callbacks.stream()
                .filter(callback -> callback.targets(claimId, pendingTask.actionId(), pendingTask.currentStageIndex()))
                .toList();
        if (matches.isEmpty()) {
            throw new IllegalStateException("No external review callback found for "
                    + claimId + "/" + pendingTask.actionId().value()
                    + "/stage-" + pendingTask.currentStageIndex());
        }

        HumanReviewTask current = pendingTask;
        for (ClaimsPrecheckBatchReviewCallback callback : matches) {
            waitForExternalReview(callback.decisionDelayMillis());
            callback.verifyToken(sharedSecret);
            try {
                current = callbackHandler.handle(callback.toHumanReviewCallback(pendingTask.runId()));
            } catch (StageAlreadyDecidedException ex) {
                current = repository.find(pendingTask.runId(), pendingTask.actionId())
                        .orElseThrow(() -> new IllegalStateException("No human review task found for duplicate "
                                + callback.describe(), ex));
                if (!isIdempotentDuplicate(callback, current)) {
                    throw new IllegalStateException("Conflicting duplicate review callback for "
                            + callback.describe(), ex);
                }
            }
        }
        if (current.decision() == HumanReviewDecision.PENDING) {
            throw new IllegalStateException("External review callbacks did not decide "
                    + claimId + "/" + pendingTask.actionId().value()
                    + "/stage-" + pendingTask.currentStageIndex());
        }
        return current;
    }

    private boolean isIdempotentDuplicate(ClaimsPrecheckBatchReviewCallback callback, HumanReviewTask task) {
        if (callback.expectedStageIndex() >= task.stageDecisions().size()) {
            return false;
        }
        StageDecision stageDecision = task.stageDecisions().get(callback.expectedStageIndex());
        return stageDecision.decision() == callback.decision()
                && stageDecision.reviewer().equals(callback.reviewer())
                && stageDecision.comment().equals(callback.comment());
    }

    private void waitForExternalReview(long delayMillis) {
        try {
            if (delayMillis > 0) {
                Thread.sleep(delayMillis);
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while simulating human review callback delay", ex);
        }
    }
}
