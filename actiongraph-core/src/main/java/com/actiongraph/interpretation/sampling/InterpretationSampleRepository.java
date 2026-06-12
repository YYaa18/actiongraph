package com.actiongraph.interpretation.sampling;

import com.actiongraph.api.Experimental;

import java.util.List;
import java.util.Optional;

/**
 * Stores sanitized interpretation quality samples outside the audit trace.
 */
@Experimental(
        since = "0.2.0",
        value = "Interpretation sampling is experimental until STD3 pilots settle."
)
public interface InterpretationSampleRepository {
    void save(InterpretationSample sample);

    List<InterpretationSample> findRecent(int limit);

    Optional<InterpretationSample> findById(String id);

    void attachRunId(String id, String runId);

    void markLabeled(String id);
}
