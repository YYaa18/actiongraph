package com.actiongraph.catalog;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class ActionGraphCompositionProfile {
    private final String name;
    private final String description;
    private final List<String> modules;
    private final List<String> notes;

    public ActionGraphCompositionProfile(
            String name,
            String description,
            List<String> modules,
            List<String> notes
    ) {
        if (isBlank(name)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        if (isBlank(description)) {
            throw new IllegalArgumentException("description must not be blank");
        }
        this.name = name;
        this.description = description;
        this.modules = copy(modules, "modules");
        this.notes = copy(notes, "notes");
    }

    public String name() {
        return name;
    }

    public String getName() {
        return name();
    }

    public String description() {
        return description;
    }

    public String getDescription() {
        return description();
    }

    public List<String> modules() {
        return modules;
    }

    public List<String> getModules() {
        return modules();
    }

    public List<String> notes() {
        return notes;
    }

    public List<String> getNotes() {
        return notes();
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        }
        if (!(other instanceof ActionGraphCompositionProfile)) {
            return false;
        }
        ActionGraphCompositionProfile that = (ActionGraphCompositionProfile) other;
        return name.equals(that.name)
                && description.equals(that.description)
                && modules.equals(that.modules)
                && notes.equals(that.notes);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, description, modules, notes);
    }

    @Override
    public String toString() {
        return "ActionGraphCompositionProfile{"
                + "name='" + name + '\''
                + ", description='" + description + '\''
                + ", modules=" + modules
                + ", notes=" + notes
                + '}';
    }

    private static List<String> copy(List<String> values, String name) {
        return Collections.unmodifiableList(new ArrayList<>(Objects.requireNonNull(values, name)));
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
