package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.action.ActionId;

import java.util.List;
import java.util.Optional;

public record ClaimsPrecheckBatchReviewOptions(
        boolean suspendResume,
        long simulatedReviewWaitMillis,
        List<ClaimsPrecheckBatchReviewDecision> externalDecisions
) {
    public ClaimsPrecheckBatchReviewOptions(boolean suspendResume, long simulatedReviewWaitMillis) {
        this(suspendResume, simulatedReviewWaitMillis, List.of());
    }

    public ClaimsPrecheckBatchReviewOptions {
        if (simulatedReviewWaitMillis < 0) {
            throw new IllegalArgumentException("simulatedReviewWaitMillis must not be negative");
        }
        externalDecisions = List.copyOf(externalDecisions == null ? List.of() : externalDecisions);
    }

    public static ClaimsPrecheckBatchReviewOptions autoApprove() {
        return new ClaimsPrecheckBatchReviewOptions(false, 0);
    }

    public static ClaimsPrecheckBatchReviewOptions suspendResume(long simulatedReviewWaitMillis) {
        return new ClaimsPrecheckBatchReviewOptions(true, simulatedReviewWaitMillis);
    }

    public static ClaimsPrecheckBatchReviewOptions externalDecisions(
            List<ClaimsPrecheckBatchReviewDecision> decisions
    ) {
        if (decisions == null || decisions.isEmpty()) {
            throw new IllegalArgumentException("external review decisions must not be empty");
        }
        return new ClaimsPrecheckBatchReviewOptions(true, 0, decisions);
    }

    public boolean hasExternalDecisions() {
        return !externalDecisions.isEmpty();
    }

    public Optional<ClaimsPrecheckBatchReviewDecision> decisionFor(
            String claimId,
            ActionId actionId,
            int stageIndex
    ) {
        return externalDecisions.stream()
                .filter(decision -> decision.claimId().equals(claimId))
                .filter(decision -> decision.actionId().equals(actionId))
                .filter(decision -> decision.stageIndex() == stageIndex)
                .findFirst();
    }

    public String modeName() {
        if (hasExternalDecisions()) {
            return "external-decisions";
        }
        return suspendResume ? "suspend-resume" : "auto-approve";
    }
}
