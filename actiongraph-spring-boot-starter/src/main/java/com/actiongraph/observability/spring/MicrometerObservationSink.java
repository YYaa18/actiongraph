package com.actiongraph.observability.spring;

import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationSink;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * Micrometer-backed {@link ObservationSink}.
 *
 * <p>The adapter records an event counter for every observation and a timer for
 * events that carry a non-zero duration. It deliberately does not tag by run id
 * to avoid unbounded metric cardinality.
 */
public final class MicrometerObservationSink implements ObservationSink {
    public static final String EVENT_COUNTER = "actiongraph.observations";
    public static final String DURATION_TIMER = "actiongraph.observation.duration";

    private final MeterRegistry meterRegistry;

    public MicrometerObservationSink(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry");
    }

    @Override
    public void observe(ObservationEvent event) {
        Tags tags = tags(event);
        Counter.builder(EVENT_COUNTER)
                .tags(tags)
                .register(meterRegistry)
                .increment();
        if (event.durationNanos() > 0) {
            Timer.builder(DURATION_TIMER)
                    .tags(tags)
                    .register(meterRegistry)
                    .record(event.durationNanos(), TimeUnit.NANOSECONDS);
        }
    }

    private Tags tags(ObservationEvent event) {
        List<Tag> tags = new ArrayList<>();
        tags.add(Tag.of("event", event.type().name()));
        if (!event.actionId().isBlank()) {
            tags.add(Tag.of("action", event.actionId()));
        }
        event.tags().entrySet().stream()
                .sorted(java.util.Map.Entry.comparingByKey())
                .forEach(entry -> tags.add(Tag.of(entry.getKey(), entry.getValue())));
        return Tags.of(tags);
    }
}
