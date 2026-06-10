package com.actiongraph.catalog;

import java.util.List;
import java.util.Optional;

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

    public Optional<ActionGraphCompositionProfile> profile(String name) {
        return catalog.profile(name);
    }
}
