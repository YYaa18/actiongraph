package com.actiongraph.observability.spring;

import com.actiongraph.api.Experimental;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import com.actiongraph.observability.ObservationSink;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanBuilder;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * OpenTelemetry-backed {@link ObservationSink} that emits one internal span per
 * ActionGraph observation.
 *
 * <p>The adapter uses the stable OpenTelemetry API and writes GenAI semantic
 * convention attribute names as strings so applications do not have to depend
 * on incubating semconv constants. It never records prompts, model output,
 * request bodies, tokens, or raw blackboard values; only the low-cardinality
 * {@link ObservationEvent#tags()} supplied by the runtime are copied.
 */
@Experimental(
        since = "0.2.0",
        value = "OpenTelemetry GenAI semantic convention export is experimental until STD2 pilots settle."
)
public final class OpenTelemetryGenAiObservationSink implements ObservationSink {
    public static final String GEN_AI_SYSTEM = "gen_ai.system";
    public static final String GEN_AI_OPERATION_NAME = "gen_ai.operation.name";
    public static final String GEN_AI_AGENT_NAME = "gen_ai.agent.name";
    public static final String ACTIONGRAPH_RUN_ID = "actiongraph.run.id";
    public static final String ACTIONGRAPH_EVENT_TYPE = "actiongraph.event.type";
    public static final String ACTIONGRAPH_ACTION_ID = "actiongraph.action.id";
    public static final String ACTIONGRAPH_DURATION_NANOS = "actiongraph.duration_nanos";
    public static final String ACTIONGRAPH_TAG_PREFIX = "actiongraph.tag.";
    public static final String ERROR_TYPE = "error.type";

    private static final String ACTIONGRAPH_SYSTEM = "actiongraph";
    private static final String DEFAULT_INSTRUMENTATION_NAME = "actiongraph";
    private static final long NANOS_PER_SECOND = 1_000_000_000L;

    private final Tracer tracer;
    private final boolean includeRunId;

    public OpenTelemetryGenAiObservationSink(OpenTelemetry openTelemetry) {
        this(openTelemetry, DEFAULT_INSTRUMENTATION_NAME, true);
    }

    public OpenTelemetryGenAiObservationSink(
            OpenTelemetry openTelemetry,
            String instrumentationName,
            boolean includeRunId
    ) {
        Objects.requireNonNull(openTelemetry, "openTelemetry");
        String safeInstrumentationName = instrumentationName == null || instrumentationName.isBlank()
                ? DEFAULT_INSTRUMENTATION_NAME
                : instrumentationName.trim();
        this.tracer = openTelemetry.getTracer(safeInstrumentationName);
        this.includeRunId = includeRunId;
    }

    @Override
    public void observe(ObservationEvent event) {
        Objects.requireNonNull(event, "event");
        Instant end = Instant.now();
        SpanBuilder builder = tracer.spanBuilder(spanName(event))
                .setSpanKind(SpanKind.INTERNAL)
                .setAttribute(GEN_AI_SYSTEM, ACTIONGRAPH_SYSTEM)
                .setAttribute(GEN_AI_AGENT_NAME, ACTIONGRAPH_SYSTEM)
                .setAttribute(GEN_AI_OPERATION_NAME, operationName(event.type()))
                .setAttribute(ACTIONGRAPH_EVENT_TYPE, event.type().name());
        if (includeRunId) {
            builder.setAttribute(ACTIONGRAPH_RUN_ID, event.runId());
        }
        if (!event.actionId().isBlank()) {
            builder.setAttribute(ACTIONGRAPH_ACTION_ID, event.actionId());
        }
        if (event.durationNanos() > 0) {
            builder.setStartTimestamp(epochNanos(end.minusNanos(event.durationNanos())), TimeUnit.NANOSECONDS);
        }

        Span span = builder.startSpan();
        try {
            if (event.durationNanos() > 0) {
                span.setAttribute(ACTIONGRAPH_DURATION_NANOS, event.durationNanos());
            }
            recordTags(span, event.tags());
            markErrorIfNeeded(span, event.tags());
        } finally {
            if (event.durationNanos() > 0) {
                span.end(epochNanos(end), TimeUnit.NANOSECONDS);
            } else {
                span.end();
            }
        }
    }

    private static void recordTags(Span span, Map<String, String> tags) {
        tags.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .filter(entry -> entry.getKey() != null && !entry.getKey().isBlank())
                .forEach(entry -> span.setAttribute(
                        ACTIONGRAPH_TAG_PREFIX + normalizeTagKey(entry.getKey()),
                        entry.getValue() == null ? "" : entry.getValue()));
    }

    private static void markErrorIfNeeded(Span span, Map<String, String> tags) {
        String success = tags.get("success");
        String errorType = firstNonBlank(tags.get("errorType"), tags.get("exceptionType"), tags.get("exception"));
        if ("false".equalsIgnoreCase(success) || errorType != null) {
            span.setStatus(StatusCode.ERROR);
            if (errorType != null) {
                span.setAttribute(ERROR_TYPE, errorType);
            }
        }
    }

    private static @Nullable String firstNonBlank(
            @Nullable String first,
            @Nullable String second,
            @Nullable String third
    ) {
        if (first != null && !first.isBlank()) {
            return first;
        }
        if (second != null && !second.isBlank()) {
            return second;
        }
        if (third != null && !third.isBlank()) {
            return third;
        }
        return null;
    }

    private static String normalizeTagKey(String key) {
        String normalized = key.trim()
                .replaceAll("[^A-Za-z0-9_.-]", "_")
                .replaceAll("_+", "_");
        return normalized.isBlank() ? "unnamed" : normalized;
    }

    private static String spanName(ObservationEvent event) {
        if (!event.actionId().isBlank()) {
            return "ActionGraph " + event.actionId() + " " + event.type().name().toLowerCase(java.util.Locale.ROOT);
        }
        return "ActionGraph " + event.type().name().toLowerCase(java.util.Locale.ROOT);
    }

    private static String operationName(ObservationEventType type) {
        return switch (type) {
            case RUN_STARTED, RUN_RESUMED, RUN_FINISHED -> "agent.run";
            case PLAN_GENERATED, NO_PLAN -> "agent.plan";
            case POLICY_EVALUATED, RUNTIME_GUARD_FAILED -> "agent.policy";
            case HUMAN_REVIEW_REQUESTED, HUMAN_REVIEW_DECIDED -> "agent.human_review";
            case ACTION_STARTED, ACTION_FINISHED -> "agent.action";
            case COMPENSATION_STARTED, COMPENSATION_FINISHED -> "agent.compensation";
            case TRACE_FLUSHED -> "agent.trace_flush";
        };
    }

    private static long epochNanos(Instant instant) {
        return Math.addExact(Math.multiplyExact(instant.getEpochSecond(), NANOS_PER_SECOND), instant.getNano());
    }
}
