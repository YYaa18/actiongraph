package com.actiongraph.policy;

import java.math.BigDecimal;
import java.util.Locale;
import java.util.Objects;

public record MonetaryAmount(BigDecimal value, String currency) {
    public MonetaryAmount {
        Objects.requireNonNull(value, "value");
        if (value.signum() < 0) {
            throw new IllegalArgumentException("amount value must not be negative");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency must not be blank");
        }
        currency = currency.trim().toUpperCase(Locale.ROOT);
    }
}
