package com.actiongraph.observability.spring;

import com.actiongraph.api.Experimental;
import com.actiongraph.observability.ObservationSink;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import com.actiongraph.spring.ActionGraphProperties;
import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.OpenTelemetry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/**
 * Optional OpenTelemetry GenAI semantic-convention integration for ActionGraph
 * observations.
 */
@AutoConfiguration(before = {ActionGraphMicrometerAutoConfiguration.class, ActionGraphAutoConfiguration.class})
@ConditionalOnClass(OpenTelemetry.class)
@ConditionalOnProperty(prefix = "actiongraph.observability.otel", name = "enabled", havingValue = "true")
@Experimental(
        since = "0.2.0",
        value = "OpenTelemetry GenAI semantic convention export is experimental until STD2 pilots settle."
)
public class ActionGraphOpenTelemetryAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public OpenTelemetry actionGraphOpenTelemetry() {
        return GlobalOpenTelemetry.get();
    }

    @Bean
    @ConditionalOnMissingBean
    public ObservationSink actionGraphObservationSink(
            OpenTelemetry openTelemetry,
            ActionGraphProperties properties
    ) {
        ActionGraphProperties.OpenTelemetryProperties otel = properties.getObservability().getOtel();
        return new OpenTelemetryGenAiObservationSink(
                openTelemetry,
                otel.getInstrumentationName(),
                otel.isIncludeRunId()
        );
    }
}
