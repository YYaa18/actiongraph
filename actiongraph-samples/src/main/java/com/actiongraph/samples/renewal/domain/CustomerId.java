package com.actiongraph.samples.renewal.domain;

import com.actiongraph.interpretation.annotation.GoalParameter;

public record CustomerId(
        @GoalParameter(
                name = "customerId",
                description = "Customer identifier. Use canonical IDs such as C001.",
                example = "C001"
        )
        String value
) {
    public CustomerId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("CustomerId value must not be blank");
        }
    }
}
