package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;

/**
 * A single golden-set example for goal interpretation evaluation.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public record GoldenCase(
        String input,
        Expectation expect
) {
    public GoldenCase {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (expect == null) {
            throw new IllegalArgumentException("expect must not be null");
        }
    }
}
