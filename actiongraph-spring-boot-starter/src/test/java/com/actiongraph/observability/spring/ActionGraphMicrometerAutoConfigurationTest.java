package com.actiongraph.observability.spring;

import com.actiongraph.action.ActionId;
import com.actiongraph.observability.NoopObservationSink;
import com.actiongraph.observability.ObservationEvent;
import com.actiongraph.observability.ObservationEventType;
import com.actiongraph.observability.ObservationSink;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ActionGraphMicrometerAutoConfigurationTest {
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    ActionGraphMicrometerAutoConfiguration.class,
                    ActionGraphAutoConfiguration.class));

    @Test
    void createsMicrometerObservationSinkWhenMeterRegistryExists() {
        contextRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .run(context -> {
                    ObservationSink sink = context.getBean(ObservationSink.class);
                    MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);

                    assertThat(sink).isInstanceOf(MicrometerObservationSink.class);

                    sink.observe(ObservationEvent.timed(
                            "RUN-1",
                            ObservationEventType.ACTION_FINISHED,
                            new ActionId("demo.complete"),
                            Map.of("success", "true"),
                            1_000
                    ));

                    assertThat(meterRegistry.find(MicrometerObservationSink.EVENT_COUNTER).counter().count())
                            .isEqualTo(1.0);
                    assertThat(meterRegistry.find(MicrometerObservationSink.DURATION_TIMER).timer().count())
                            .isEqualTo(1);
                });
    }

    @Test
    void backsOffWhenApplicationProvidesObservationSink() {
        ObservationSink customSink = event -> {
        };

        contextRunner
                .withBean(MeterRegistry.class, SimpleMeterRegistry::new)
                .withBean(ObservationSink.class, () -> customSink)
                .run(context -> assertThat(context.getBean(ObservationSink.class))
                        .isSameAs(customSink)
                        .isNotSameAs(NoopObservationSink.INSTANCE));
    }
}
