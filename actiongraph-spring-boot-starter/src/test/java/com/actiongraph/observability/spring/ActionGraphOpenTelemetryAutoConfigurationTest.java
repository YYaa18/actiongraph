package com.actiongraph.observability.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.observability.NoopObservationSink;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import com.actiongraph.observability.ObservationSink;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphOpenTelemetryAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphOpenTelemetryAutoConfiguration.class,
                    ActionGraphMicrometerAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class));

    @Test
    void createsOpenTelemetryObservationSinkWhenEnabled() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = tracerProvider(exporter);
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        try {
            contextRunner
                    .withBean(OpenTelemetry.class, () -> openTelemetry)
                    .withPropertyValues(
                            "actiongraph.observability.otel.enabled=true",
                            "actiongraph.observability.otel.instrumentation-name=actiongraph-test"
                    )
                    .run(context -> {
                        ObservationSink sink = context.getBean(ObservationSink.class);

                        assertThat(sink).isInstanceOf(OpenTelemetryGenAiObservationSink.class);

                        sink.observe(ObservationEvent.of(
                                "RUN-OTEL-AUTO-1",
                                ObservationEventType.PLAN_GENERATED,
                                new ActionId("demo.plan"),
                                Map.of("planLength", "2")
                        ));

                        assertThat(exporter.getFinishedSpanItems())
                                .singleElement()
                                .satisfies(span -> assertThat(span.getAttributes()
                                        .get(AttributeKey.stringKey(OpenTelemetryGenAiObservationSink.GEN_AI_OPERATION_NAME)))
                                        .isEqualTo("agent.plan"));
                    });
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void openTelemetrySinkWinsOverMicrometerWhenExplicitlyEnabled() {
        InMemorySpanExporter exporter = InMemorySpanExporter.create();
        SdkTracerProvider tracerProvider = tracerProvider(exporter);
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(tracerProvider)
                .build();

        try {
            contextRunner
                    .withBean(OpenTelemetry.class, () -> openTelemetry)
                    .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                    .withPropertyValues("actiongraph.observability.otel.enabled=true")
                    .run(context -> assertThat(context.getBean(ObservationSink.class))
                            .isInstanceOf(OpenTelemetryGenAiObservationSink.class)
                            .isNotSameAs(NoopObservationSink.INSTANCE));
        } finally {
            tracerProvider.close();
        }
    }

    @Test
    void backsOffWhenApplicationProvidesObservationSink() {
        ObservationSink customSink = event -> {
        };

        contextRunner
                .withBean(ObservationSink.class, () -> customSink)
                .withPropertyValues("actiongraph.observability.otel.enabled=true")
                .run(context -> assertThat(context.getBean(ObservationSink.class)).isSameAs(customSink));
    }

    private static SdkTracerProvider tracerProvider(InMemorySpanExporter exporter) {
        return SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
    }
}
