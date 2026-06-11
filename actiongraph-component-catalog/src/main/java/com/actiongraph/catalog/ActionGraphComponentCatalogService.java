package com.actiongraph.catalog;

import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public final class ActionGraphComponentCatalogService {
    private final ActionGraphComponentCatalog catalog;

    public ActionGraphComponentCatalogService(ActionGraphComponentCatalog catalog) {
        this.catalog = catalog;
    }

    public static ActionGraphComponentCatalogService defaultCatalog() {
        return new ActionGraphComponentCatalogService(DefaultActionGraphComponentCatalog.create());
    }

    public ActionGraphComponentCatalog catalog() {
        return catalog;
    }

    public List<ActionGraphComponent> components() {
        return catalog.components();
    }

    public List<ActionGraphCompositionProfile> profiles() {
        return catalog.profiles();
    }

    public Optional<ActionGraphComponent> component(String module) {
        return catalog.component(module);
    }

    public List<ActionGraphComponent> componentsByCompatibility(String compatibility) {
        if (isBlank(compatibility)) {
            return Collections.emptyList();
        }
        return catalog.components().stream()
                .filter(component -> component.compatibility().equals(compatibility))
                .collect(Collectors.toList());
    }

    public List<ActionGraphCompositionProfile> profilesContainingModule(String module) {
        if (isBlank(module)) {
            return Collections.emptyList();
        }
        return catalog.profiles().stream()
                .filter(profile -> profile.modules().contains(module))
                .collect(Collectors.toList());
    }

    public Optional<ActionGraphCompositionProfile> profile(String name) {
        return catalog.profile(name);
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }
}
