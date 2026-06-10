package com.actiongraph.samples.renewal.domain;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public record QuoteDraft(String quoteId, CustomerId customerId, BigDecimal premium, String currency) {
    public QuoteDraft(String quoteId, CustomerId customerId) {
        this(quoteId, customerId, BigDecimal.valueOf(120_000), "CNY");
    }

    public QuoteDraft {
        if (quoteId == null || quoteId.isBlank()) {
            throw new IllegalArgumentException("quoteId must not be blank");
        }
        Objects.requireNonNull(customerId, "customerId");
        Objects.requireNonNull(premium, "premium");
        if (premium.signum() < 0) {
            throw new IllegalArgumentException("premium must not be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }
}
