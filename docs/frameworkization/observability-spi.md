# ActionGraph Observability SPI

ActionGraph exposes runtime observations through `ObservationSink` in
`actiongraph-core`. The core module does not depend on Micrometer, OpenTelemetry,
Spring, or any monitoring backend.

## Core Contract

Implement `com.actiongraph.observability.ObservationSink` when an application
wants counters, timers, logs, or internal monitoring hooks:

```java
ObservationSink sink = event -> {
    // event.type(), event.actionId(), event.tags(), event.durationNanos()
};

GoapExecutor executor = GoapExecutor.builder()
        .observationSink(sink)
        .build();
```

The runtime catches sink exceptions. Observability failure must not break
business execution, compensation, or suspend/resume.

## Event Shape

`ObservationEvent` carries:

- `runId`: correlation id, not intended as a metric tag by default;
- `type`: lifecycle event such as `RUN_FINISHED`, `ACTION_FINISHED`, `TRACE_FLUSHED`;
- `actionId`: action id for action-scoped events;
- `tags`: low-cardinality dimensions such as `status`, `decision`, `success`, `riskLevel`;
- `durationNanos`: elapsed time for completed operations when available.

Do not put raw PII, prompts, model output, request bodies, tokens, or unbounded
business ids into tags. Keep those in trace after masking, not in metrics.

## Spring Boot

`actiongraph-spring-boot-starter` creates `NoopObservationSink` by default. If
the application provides an `ObservationSink` bean, the default executor uses it.

```java
@Bean
ObservationSink actionGraphObservationSink(MyMetrics metrics) {
    return event -> metrics.count(event.type().name(), event.tags());
}
```

## Micrometer

When `MeterRegistry` is on the classpath, the Spring starter auto-configures a
`MicrometerObservationSink` unless the application already provides its own
`ObservationSink`.

The Micrometer adapter records:

- counter: `actiongraph.observations`;
- timer: `actiongraph.observation.duration`.

The adapter tags by event type, action id when present, and the low-cardinality
event tags. It deliberately does not tag by `runId`.

## Batch Token Efficiency

Batch token efficiency belongs in the application-owned `BatchGoalInterpreter`
implementation. Observability can record batch sizes, cache hits, model name, or
fallback mode as low-cardinality tags, but raw inputs and per-row ids should stay
out of metrics.
