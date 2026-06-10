package com.actiongraph.samples.claimsprecheck.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public record ClaimRecord(
        ClaimId claimId,
        String policyNo,
        String customerName,
        BigDecimal claimedAmount,
        String currency,
        boolean closed
) {
    public ClaimRecord {
        Objects.requireNonNull(claimId, "claimId");
        if (policyNo == null || policyNo.isBlank()) {
            throw new IllegalArgumentException("policyNo must not be blank");
        }
        customerName = customerName == null ? "" : customerName;
        Objects.requireNonNull(claimedAmount, "claimedAmount");
        if (claimedAmount.signum() < 0) {
            throw new IllegalArgumentException("claimedAmount must not be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }
}
