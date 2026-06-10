package com.actiongraph.catalog;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public final class ActionGraphComponentCatalog {
    private final List<ActionGraphComponent> components;
    private final List<ActionGraphCompositionProfile> profiles;

    public ActionGraphComponentCatalog(
            List<ActionGraphComponent> components,
            List<ActionGraphCompositionProfile> profiles
    ) {
        this.components = List.copyOf(Objects.requireNonNull(components, "components"));
        this.profiles = List.copyOf(Objects.requireNonNull(profiles, "profiles"));
        assertUniqueComponents(this.components);
        assertUniqueProfiles(this.profiles);
    }

    public List<ActionGraphComponent> components() {
        return components;
    }

    public List<ActionGraphComponent> getComponents() {
        return components();
    }

    public List<ActionGraphCompositionProfile> profiles() {
        return profiles;
    }

    public List<ActionGraphCompositionProfile> getProfiles() {
        return profiles();
    }

    public Optional<ActionGraphComponent> component(String module) {
        if (module == null || module.isBlank()) {
            return Optional.empty();
        }
        return components.stream()
                .filter(component -> component.module().equals(module))
                .findFirst();
    }

    public Optional<ActionGraphCompositionProfile> profile(String name) {
        if (name == null || name.isBlank()) {
            return Optional.empty();
        }
        return profiles.stream()
                .filter(profile -> profile.name().equals(name))
                .findFirst();
    }

    private void assertUniqueComponents(List<ActionGraphComponent> components) {
        List<String> duplicates = components.stream()
                .map(ActionGraphComponent::module)
                .sorted()
                .filter(new DuplicateFilter()::isDuplicate)
                .distinct()
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate components: " + duplicates);
        }
    }

    private void assertUniqueProfiles(List<ActionGraphCompositionProfile> profiles) {
        List<String> duplicates = profiles.stream()
                .map(ActionGraphCompositionProfile::name)
                .sorted(Comparator.naturalOrder())
                .filter(new DuplicateFilter()::isDuplicate)
                .distinct()
                .toList();
        if (!duplicates.isEmpty()) {
            throw new IllegalArgumentException("duplicate profiles: " + duplicates);
        }
    }

    private static final class DuplicateFilter {
        private String previous;

        private boolean isDuplicate(String value) {
            boolean duplicate = Objects.equals(previous, value);
            previous = value;
            return duplicate;
        }
    }
}
