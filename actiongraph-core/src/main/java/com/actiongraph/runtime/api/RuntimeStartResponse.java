package com.actiongraph.runtime.api;

import java.util.Objects;
import java.util.Optional;

public record RuntimeStartResponse(
        RuntimeStartDisposition disposition,
        RuntimeInterpretationResponse interpretation,
        Optional<RuntimeRunResponse> run
) {
    public RuntimeStartResponse {
        Objects.requireNonNull(disposition, "disposition");
        Objects.requireNonNull(interpretation, "interpretation");
        run = Objects.requireNonNull(run, "run");
    }

    public static RuntimeStartResponse clarificationRequired(RuntimeInterpretationResponse interpretation) {
        return new RuntimeStartResponse(
                RuntimeStartDisposition.CLARIFICATION_REQUIRED,
                interpretation,
                Optional.empty()
        );
    }

    public static RuntimeStartResponse runStarted(
            RuntimeInterpretationResponse interpretation,
            RuntimeRunResponse run
    ) {
        return new RuntimeStartResponse(
                RuntimeStartDisposition.RUN_STARTED,
                interpretation,
                Optional.of(Objects.requireNonNull(run, "run"))
        );
    }
}
