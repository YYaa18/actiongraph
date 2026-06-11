package com.actiongraph.observability;

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Observation sink that fan-outs events to multiple sinks in registration order.
 */
public final class CompositeObservationSink implements ObservationSink {
    private final List<ObservationSink> sinks;

    public CompositeObservationSink(ObservationSink... sinks) {
        this(Arrays.asList(Objects.requireNonNull(sinks, "sinks")));
    }

    public CompositeObservationSink(List<? extends ObservationSink> sinks) {
        Objects.requireNonNull(sinks, "sinks");
        if (sinks.stream().anyMatch(Objects::isNull)) {
            throw new NullPointerException("sinks must not contain null");
        }
        this.sinks = List.copyOf(sinks);
    }

    @Override
    public void observe(ObservationEvent event) {
        for (ObservationSink sink : sinks) {
            sink.observe(event);
        }
    }
}
