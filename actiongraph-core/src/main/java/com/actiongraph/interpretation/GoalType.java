package com.actiongraph.interpretation;

public record GoalType(String value) {
    public GoalType {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("GoalType value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
