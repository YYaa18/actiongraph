package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;

import java.util.List;

/**
 * Machine-readable and markdown-friendly difference for one failed case.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public record CaseDiff(
        String input,
        Expectation expected,
        ActualInterpretation actual,
        List<String> differences
) {
    public CaseDiff {
        if (input == null || input.isBlank()) {
            throw new IllegalArgumentException("input must not be blank");
        }
        if (expected == null) {
            throw new IllegalArgumentException("expected must not be null");
        }
        if (actual == null) {
            throw new IllegalArgumentException("actual must not be null");
        }
        differences = differences == null ? List.of() : List.copyOf(differences);
    }
}
