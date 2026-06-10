package com.actiongraph.interpretation;

public record MissingField(String name) {
    public MissingField {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("MissingField name must not be blank");
        }
    }
}
