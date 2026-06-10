package com.actiongraph.samples.ordercancellation.domain;

public record OrderId(String value) {
    public OrderId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("OrderId value must not be blank");
        }
    }
}
