package com.actiongraph.observability.spring;

import com.actiongraph.observability.ObservationSink;
import com.actiongraph.spring.ActionGraphAutoConfiguration;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * Optional Micrometer integration for ActionGraph observations.
 */
@AutoConfiguration(before = ActionGraphAutoConfiguration.class)
@ConditionalOnClass(MeterRegistry.class)
public class ActionGraphMicrometerAutoConfiguration {
    @Bean
    @ConditionalOnMissingBean
    public ObservationSink actionGraphObservationSink(MeterRegistry meterRegistry) {
        return new MicrometerObservationSink(meterRegistry);
    }
}
