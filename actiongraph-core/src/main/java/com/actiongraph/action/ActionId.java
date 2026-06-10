package com.actiongraph.action;

public record ActionId(String value) {
    public ActionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ActionId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
