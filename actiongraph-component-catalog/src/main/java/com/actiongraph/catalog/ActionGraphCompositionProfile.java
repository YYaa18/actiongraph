package com.actiongraph.catalog;

import java.util.List;
import java.util.Objects;

public record ActionGraphCompositionProfile(
        String name,
        String description,
        List<String> modules,
        List<String> notes
) {
    public ActionGraphCompositionProfile {
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        modules = List.copyOf(Objects.requireNonNull(modules, "modules"));
        notes = List.copyOf(Objects.requireNonNull(notes, "notes"));
    }
}
