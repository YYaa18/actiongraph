package com.actiongraph.samples.claimsprecheck.batch;

import com.actiongraph.action.ActionId;
import com.actiongraph.policy.HumanReviewCallback;
import com.actiongraph.policy.HumanReviewDecision;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;

public record ClaimsPrecheckBatchReviewCallback(
        String deliveryId,
        String claimId,
        String runId,
        ActionId actionId,
        int expectedStageIndex,
        HumanReviewDecision decision,
        String reviewer,
        String comment,
        long decisionDelayMillis,
        String token
) {
    public ClaimsPrecheckBatchReviewCallback {
        deliveryId = deliveryId == null ? "" : deliveryId;
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("claimId must not be blank");
        }
        runId = runId == null ? "" : runId;
        Objects.requireNonNull(actionId, "actionId");
        if (expectedStageIndex < 0) {
            throw new IllegalArgumentException("expectedStageIndex must not be negative");
        }
        Objects.requireNonNull(decision, "decision");
        if (decision == HumanReviewDecision.PENDING) {
            throw new IllegalArgumentException("review callback decision must be APPROVED or DENIED");
        }
        reviewer = reviewer == null || reviewer.isBlank() ? "external-reviewer" : reviewer;
        comment = comment == null ? "" : comment;
        if (decisionDelayMillis < 0) {
            throw new IllegalArgumentException("decisionDelayMillis must not be negative");
        }
        token = token == null ? "" : token;
    }

    public boolean targets(String candidateClaimId, ActionId candidateActionId, int candidateStageIndex) {
        return claimId.equals(candidateClaimId)
                && actionId.equals(candidateActionId)
                && expectedStageIndex == candidateStageIndex;
    }

    public void verifyToken(String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            return;
        }
        if (!sameSecret(sharedSecret, token)) {
            throw new SecurityException("Invalid review callback token for " + describe());
        }
    }

    public HumanReviewCallback toHumanReviewCallback(String actualRunId) {
        String callbackRunId = resolvedRunId(actualRunId);
        return new HumanReviewCallback(
                callbackRunId,
                actionId,
                expectedStageIndex,
                decision,
                reviewer,
                comment
        );
    }

    public String resolvedRunId(String actualRunId) {
        if (runId.isBlank() || "$RUN_ID".equals(runId) || "${runId}".equals(runId)) {
            return actualRunId;
        }
        if (!runId.equals(actualRunId)) {
            throw new IllegalStateException("Review callback " + describe()
                    + " targets runId " + runId + " but pending task uses " + actualRunId);
        }
        return runId;
    }

    public String describe() {
        String prefix = deliveryId.isBlank() ? "" : deliveryId + " ";
        return prefix + claimId + "/" + actionId.value() + "/stage-" + expectedStageIndex;
    }

    private static boolean sameSecret(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null
                ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
