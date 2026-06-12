package com.actiongraph.interpretation.sampling;

import com.actiongraph.api.Experimental;

import java.time.Instant;
import java.util.Set;

import org.jspecify.annotations.Nullable;

/**
 * Sanitized production interpretation sample for quality engineering.
 */
@Experimental(
        since = "0.2.0",
        value = "Interpretation sampling is experimental until STD3 pilots settle."
)
public record InterpretationSample(
        String id,
        Instant at,
        String maskedInput,
        String outcome,
        String goalType,
        Set<String> missingFields,
        boolean fallbackUsed,
        boolean parseFailure,
        @Nullable String runId,
        boolean labeled
) {
    public InterpretationSample {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("id must not be blank");
        }
        at = java.util.Objects.requireNonNull(at, "at");
        maskedInput = maskedInput == null ? "" : maskedInput;
        outcome = outcome == null ? "" : outcome;
        goalType = goalType == null ? "" : goalType;
        missingFields = missingFields == null ? Set.of() : Set.copyOf(missingFields);
        runId = runId == null || runId.isBlank() ? null : runId;
    }

    public InterpretationSample withRunId(String newRunId) {
        return new InterpretationSample(
                id,
                at,
                maskedInput,
                outcome,
                goalType,
                missingFields,
                fallbackUsed,
                parseFailure,
                newRunId,
                labeled
        );
    }

    public InterpretationSample withLabeled() {
        return new InterpretationSample(
                id,
                at,
                maskedInput,
                outcome,
                goalType,
                missingFields,
                fallbackUsed,
                parseFailure,
                runId,
                true
        );
    }
}
