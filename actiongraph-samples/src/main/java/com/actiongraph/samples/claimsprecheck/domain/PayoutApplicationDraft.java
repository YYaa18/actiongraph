package com.actiongraph.samples.claimsprecheck.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public record PayoutApplicationDraft(
        String draftId,
        ClaimId claimId,
        BigDecimal amount,
        String currency
) {
    public PayoutApplicationDraft {
        if (draftId == null || draftId.isBlank()) {
            throw new IllegalArgumentException("draftId must not be blank");
        }
        Objects.requireNonNull(claimId, "claimId");
        Objects.requireNonNull(amount, "amount");
        if (amount.signum() < 0) {
            throw new IllegalArgumentException("amount must not be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }
}
