package com.actiongraph.interpretation.sampling;

import com.actiongraph.api.Experimental;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Thread-safe in-memory interpretation sample repository for tests and local
 * development.
 */
@Experimental(
        since = "0.2.0",
        value = "Interpretation sampling is experimental until STD3 pilots settle."
)
public final class InMemoryInterpretationSampleRepository implements InterpretationSampleRepository {
    private final Map<String, InterpretationSample> samples = new LinkedHashMap<>();

    @Override
    public synchronized void save(InterpretationSample sample) {
        samples.put(sample.id(), sample);
    }

    @Override
    public synchronized List<InterpretationSample> findRecent(int limit) {
        if (limit <= 0) {
            throw new IllegalArgumentException("limit must be positive");
        }
        List<InterpretationSample> values = new ArrayList<>(samples.values());
        int from = Math.max(0, values.size() - limit);
        return List.copyOf(values.subList(from, values.size()).reversed());
    }

    @Override
    public synchronized Optional<InterpretationSample> findById(String id) {
        return Optional.ofNullable(samples.get(id));
    }

    @Override
    public synchronized void attachRunId(String id, String runId) {
        InterpretationSample sample = samples.get(id);
        if (sample != null) {
            samples.put(id, sample.withRunId(runId));
        }
    }

    @Override
    public synchronized void markLabeled(String id) {
        InterpretationSample sample = samples.get(id);
        if (sample != null) {
            samples.put(id, sample.withLabeled());
        }
    }
}
