package com.actiongraph.interpretation.sampling;

import com.actiongraph.api.Experimental;

import java.util.Optional;

/**
 * Optional hook for linking an interpretation sample to a run started later on
 * the same thread.
 */
@Experimental(
        since = "0.2.0",
        value = "Interpretation sampling is experimental until STD3 pilots settle."
)
public interface InterpretationSampleTracker {
    Optional<String> lastSampleId();

    void attachRunId(String sampleId, String runId);
}
