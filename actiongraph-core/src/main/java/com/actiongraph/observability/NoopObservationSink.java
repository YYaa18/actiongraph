package com.actiongraph.observability;

/**
 * Observation sink that drops all events.
 */
public enum NoopObservationSink implements ObservationSink {
    INSTANCE;

    @Override
    public void observe(ObservationEvent event) {
        // no-op
    }
}
