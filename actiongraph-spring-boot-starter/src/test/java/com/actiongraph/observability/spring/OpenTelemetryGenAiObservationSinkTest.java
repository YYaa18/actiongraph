package com.actiongraph.observability.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.data.SpanData;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class OpenTelemetryGenAiObservationSinkTest {
    @Test
    void emitsGenAiSemanticAttributesWithoutBusinessPayload() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        try {
            OpenTelemetryGenAiObservationSink sink =
                    new OpenTelemetryGenAiObservationSink(openTelemetry, "actiongraph-test", true);

            sink.observe(ObservationEvent.timed(
                    "RUN-OTEL-1",
                    ObservationEventType.ACTION_FINISHED,
                    new ActionId("payments.reserve"),
                    Map.of(
                            "success", "false",
                            "exceptionType", "TimeoutException",
                            "risk level", "HIGH"
                    ),
                    5_000_000
            ));

            assertThat(exporter.getFinishedSpanItems())
                    .singleElement()
                    .satisfies(span -> {
                        assertThat(span.getName()).isEqualTo("ActionGraph payments.reserve action_finished");
                        assertThat(span.getStatus().getStatusCode()).isEqualTo(StatusCode.ERROR);
                        assertThat(span.getEndEpochNanos() - span.getStartEpochNanos()).isEqualTo(5_000_000);
                        assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.GEN_AI_SYSTEM)))
                                .isEqualTo("actiongraph");
                        assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.GEN_AI_OPERATION_NAME)))
                                .isEqualTo("agent.action");
                        assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.ACTIONGRAPH_RUN_ID)))
                                .isEqualTo("RUN-OTEL-1");
                        assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.ACTIONGRAPH_ACTION_ID)))
                                .isEqualTo("payments.reserve");
                        assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.ACTIONGRAPH_EVENT_TYPE)))
                                .isEqualTo("ACTION_FINISHED");
                        assertThat(span.getAttributes().get(longKey(OpenTelemetryGenAiObservationSink.ACTIONGRAPH_DURATION_NANOS)))
                                .isEqualTo(5_000_000);
                        assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.ERROR_TYPE)))
                                .isEqualTo("TimeoutException");
                        assertThat(span.getAttributes().get(string("actiongraph.tag.risk_level")))
                                .isEqualTo("HIGH");
                    });
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void canSuppressRunIdAttributeForStrictCardinalityPolicies() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        try {
            OpenTelemetryGenAiObservationSink sink =
                    new OpenTelemetryGenAiObservationSink(openTelemetry, "actiongraph-test", false);

            sink.observe(ObservationEvent.of(
                    "RUN-OTEL-2",
                    ObservationEventType.RUN_STARTED,
                    null,
                    Map.of("goalType", "renewal.quote")
            ));

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.ACTIONGRAPH_RUN_ID)))
                    .isNull();
            assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.GEN_AI_OPERATION_NAME)))
                    .isEqualTo("agent.run");
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void mapsInterpretationEventsToGenAiInterpretationOperation() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        try {
            OpenTelemetryGenAiObservationSink sink =
                    new OpenTelemetryGenAiObservationSink(openTelemetry, "actiongraph-test", true);

            sink.observe(ObservationEvent.of(
                    "INTERPRETATION-1",
                    ObservationEventType.INTERPRETATION_FINISHED,
                    null,
                    Map.of("outcome", "ready")
            ));

            SpanData span = exporter.getFinishedSpanItems().get(0);
            assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.GEN_AI_OPERATION_NAME)))
                    .isEqualTo("agent.interpretation");
            assertThat(span.getAttributes().get(string(OpenTelemetryGenAiObservationSink.ACTIONGRAPH_RUN_ID)))
                    .isNull();
        } finally {
            tracerProvider.close();
        }
    }

    private static AttributeKey<String> string(String key) {
        return AttributeKey.stringKey(key);
    }

    private static AttributeKey<Long> longKey(String key) {
        return AttributeKey.longKey(key);
    }
}
