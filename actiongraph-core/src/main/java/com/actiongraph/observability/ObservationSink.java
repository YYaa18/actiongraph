package com.actiongraph.observability;

/**
 * Receives runtime lifecycle observations for metrics and operational
 * monitoring.
 *
 * <p>The SPI is deliberately provider-neutral. Applications can implement it
 * with Micrometer, OpenTelemetry, logs, in-house counters, or tests. Sinks
 * should be thread-safe when registered as singletons and should avoid blocking
 * the runtime path. The default executor catches sink exceptions so monitoring
 * failures do not execute business actions twice or break compensation.
 */
@FunctionalInterface
public interface ObservationSink {
    /**
     * Records one observation.
     *
     * @param event observation event; never {@code null}
     */
    void observe(ObservationEvent event);
}
