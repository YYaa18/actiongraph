package com.actiongraph.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ActionGraphComponent {
    private final String module;
    private final ComponentKind kind;
    private final String description;
    private final List<String> capabilities;
    private final List<String> requires;
    private final List<String> optionalWith;
    private final String compatibility;

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

    public ActionGraphComponent(
            String module,
            ComponentKind kind,
            String description,
            List<String> capabilities,
            List<String> requires,
            List<String> optionalWith,
            String compatibility
    ) {
        if (isBlank(module)) {
            throw new IllegalArgumentException("module must not be blank");
        }
        if (isBlank(description)) {
            throw new IllegalArgumentException("description must not be blank");
        }
        if (isBlank(compatibility)) {
            throw new IllegalArgumentException("compatibility must not be blank");
        }
        this.module = module;
        this.kind = Objects.requireNonNull(kind, "kind");
        this.description = description;
        this.capabilities = copy(capabilities, "capabilities");
        this.requires = copy(requires, "requires");
        this.optionalWith = copy(optionalWith, "optionalWith");
        this.compatibility = compatibility;
    }

    public String module() {
        return module;
    }

    public String getModule() {
        return module();
    }

    public ComponentKind kind() {
        return kind;
    }

    public ComponentKind getKind() {
        return kind();
    }

    public String description() {
        return description;
    }

    public String getDescription() {
        return description();
    }

    public List<String> capabilities() {
        return capabilities;
    }

    public List<String> getCapabilities() {
        return capabilities();
    }

    public List<String> requires() {
        return requires;
    }

    public List<String> getRequires() {
        return requires();
    }

    public List<String> optionalWith() {
        return optionalWith;
    }

    public List<String> getOptionalWith() {
        return optionalWith();
    }

    public String compatibility() {
        return compatibility;
    }

    public String getCompatibility() {
        return compatibility();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActionGraphComponent)) {
            return false;
        }
        ActionGraphComponent that = (ActionGraphComponent) other;
        return module.equals(that.module)
                && kind == that.kind
                && description.equals(that.description)
                && capabilities.equals(that.capabilities)
                && requires.equals(that.requires)
                && optionalWith.equals(that.optionalWith)
                && compatibility.equals(that.compatibility);
    }

    @Override
    public int hashCode() {
        return Objects.hash(module, kind, description, capabilities, requires, optionalWith, compatibility);
    }

    @Override
    public String toString() {
        return "ActionGraphComponent{"
                + "module='" + module + '\''
                + ", kind=" + kind
                + ", description='" + description + '\''
                + ", capabilities=" + capabilities
                + ", requires=" + requires
                + ", optionalWith=" + optionalWith
                + ", compatibility='" + compatibility + '\''
                + '}';
    }

    private static List<String> copy(List<String> values, String name) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values, name)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
