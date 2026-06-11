package com.actiongraph.catalog;

public enum ComponentCompatibility {
    NO_RUNTIME_CODE("no-runtime-code"),
    JAVA8_CLIENT("java8-client"),
    JAVA8_RUNTIME("java8-runtime"),
    JAVA21_PLUS("java21-plus"),
    SAMPLE_ONLY("sample-only");

    private final String label;

    ComponentCompatibility(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
