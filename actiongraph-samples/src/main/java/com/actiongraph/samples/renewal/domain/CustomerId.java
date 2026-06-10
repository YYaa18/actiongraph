package com.actiongraph.samples.renewal.domain;

public record CustomerId(String value) {
    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId value must not be blank");
        }
    }
}
