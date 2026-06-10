package com.actiongraph.samples.claimsprecheck.batch;

import java.math.BigDecimal;
import java.util.Objects;

public record ClaimsPrecheckBatchCase(
        String claimId,
        BigDecimal claimedAmount,
        boolean missingInvoice,
        boolean closed,
        boolean approvalFails,
        boolean expectedIntercept
) {
    public ClaimsPrecheckBatchCase {
        if (claimId == null || claimId.isBlank()) {
            throw new IllegalArgumentException("claimId must not be blank");
        }
        Objects.requireNonNull(claimedAmount, "claimedAmount");
        if (claimedAmount.signum() < 0) {
            throw new IllegalArgumentException("claimedAmount must not be negative");
        }
        claimId = claimId.trim().toUpperCase();
    }

    public static ClaimsPrecheckBatchCase normal(String claimId, String amount) {
        return new ClaimsPrecheckBatchCase(claimId, new BigDecimal(amount), false, false, false, false);
    }

    public static ClaimsPrecheckBatchCase missingInvoice(String claimId, String amount) {
        return new ClaimsPrecheckBatchCase(claimId, new BigDecimal(amount), true, false, false, true);
    }

    public static ClaimsPrecheckBatchCase closed(String claimId, String amount) {
        return new ClaimsPrecheckBatchCase(claimId, new BigDecimal(amount), false, true, false, true);
    }

    public static ClaimsPrecheckBatchCase aboveHardLimit(String claimId, String amount) {
        return new ClaimsPrecheckBatchCase(claimId, new BigDecimal(amount), false, false, false, true);
    }

    public static ClaimsPrecheckBatchCase approvalFailure(String claimId, String amount) {
        return new ClaimsPrecheckBatchCase(claimId, new BigDecimal(amount), false, false, true, false);
    }
}
