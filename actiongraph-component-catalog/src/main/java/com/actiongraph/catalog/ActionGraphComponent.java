package com.actiongraph.catalog;

import java.util.List;
import java.util.Objects;

public record ActionGraphComponent(
        String module,
        ComponentKind kind,
        String description,
        List<String> capabilities,
        List<String> requires,
        List<String> optionalWith,
        String compatibility
) {
    public ActionGraphComponent(
            String module,
            ComponentKind kind,
            String description,
            List<String> capabilities,
            List<String> requires,
            List<String> optionalWith
    ) {
        this(module, kind, description, capabilities, requires, optionalWith,
                ComponentCompatibility.JAVA21_PLUS.label());
    }

    public ActionGraphComponent {
        if (module == null || module.isBlank()) {
            throw new IllegalArgumentException("module must not be blank");
        }
        Objects.requireNonNull(kind, "kind");
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (compatibility == null || compatibility.isBlank()) {
            throw new IllegalArgumentException("compatibility must not be blank");
        }
        capabilities = List.copyOf(Objects.requireNonNull(capabilities, "capabilities"));
        requires = List.copyOf(Objects.requireNonNull(requires, "requires"));
        optionalWith = List.copyOf(Objects.requireNonNull(optionalWith, "optionalWith"));
    }
}
