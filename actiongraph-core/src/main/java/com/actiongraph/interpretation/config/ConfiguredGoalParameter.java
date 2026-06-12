package com.actiongraph.interpretation.config;

import com.actiongraph.api.Experimental;

/**
 * External goal parameter declaration loaded from configuration or a bundle.
 */
@Experimental(
        since = "0.2.0",
        value = "External goal configuration is experimental until DX4 pilots validate bundle conventions."
)
public record ConfiguredGoalParameter(
        String name,
        String type,
        boolean required,
        String description,
        String example
) {
    public ConfiguredGoalParameter {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("configured goal parameter name must not be blank");
        }
        type = type == null || type.isBlank() ? "string" : type.trim();
        description = description == null ? "" : description;
        example = example == null ? "" : example;
    }
}
