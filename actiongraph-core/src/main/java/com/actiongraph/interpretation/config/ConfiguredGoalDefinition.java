package com.actiongraph.interpretation.config;

import com.actiongraph.api.Experimental;

import java.util.List;

/**
 * External goal declaration loaded from configuration, studio output, or a bundle.
 */
@Experimental(
        since = "0.2.0",
        value = "External goal configuration is experimental until DX4 pilots validate bundle conventions."
)
public record ConfiguredGoalDefinition(
        String type,
        String description,
        boolean enabled,
        List<String> targetConditions,
        List<String> seedConditions,
        List<ConfiguredGoalParameter> parameters,
        String source
) {
    public ConfiguredGoalDefinition {
        if (type == null || type.isBlank()) {
            throw new IllegalArgumentException("configured goal type must not be blank");
        }
        description = description == null ? "" : description;
        targetConditions = List.copyOf(targetConditions == null ? List.of() : targetConditions);
        seedConditions = List.copyOf(seedConditions == null ? List.of() : seedConditions);
        parameters = List.copyOf(parameters == null ? List.of() : parameters);
        source = source == null || source.isBlank() ? "external goal " + type : source;
    }
}
