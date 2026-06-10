package com.actiongraph.runtime;

import java.util.Objects;

public record BlackboardKey<T>(Class<T> type, String id) {
    public static final String DEFAULT_ID = "default";

    public BlackboardKey {
        Objects.requireNonNull(type, "type");
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Blackboard key id must not be blank");
        }
    }

    public static <T> BlackboardKey<T> of(Class<T> type) {
        return new BlackboardKey<>(type, DEFAULT_ID);
    }

    public static <T> BlackboardKey<T> of(Class<T> type, String id) {
        return new BlackboardKey<>(type, id);
    }

    public boolean isDefault() {
        return DEFAULT_ID.equals(id);
    }

    public String displayName() {
        return type.getSimpleName() + "#" + id;
    }
}
