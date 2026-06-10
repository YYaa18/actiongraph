package com.actiongraph.governance;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public record AmountLimitRule(
        String actionId,
        String currency,
        BigDecimal hardLimit,
        BigDecimal reviewLimit
) {
    public static final String ANY_ACTION = "*";

    public AmountLimitRule {
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId must not be blank");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        Objects.requireNonNull(hardLimit, "hardLimit");
        Objects.requireNonNull(reviewLimit, "reviewLimit");
        if (hardLimit.signum() < 0) {
            throw new IllegalArgumentException("hardLimit must not be negative");
        }
        if (reviewLimit.signum() < 0) {
            throw new IllegalArgumentException("reviewLimit must not be negative");
        }
        if (reviewLimit.compareTo(hardLimit) > 0) {
            throw new IllegalArgumentException("reviewLimit must be <= hardLimit");
        }
        actionId = actionId.trim();
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }

    boolean matchesAction(String candidateActionId) {
        return ANY_ACTION.equals(actionId) || actionId.equals(candidateActionId);
    }
}
