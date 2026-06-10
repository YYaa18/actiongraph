package com.company.deployment;

import com.actiongraph.catalog.ActionGraphComponent;
import com.actiongraph.catalog.ActionGraphComponentCatalogService;
import com.actiongraph.catalog.ActionGraphCompositionProfile;
import com.actiongraph.catalog.ComponentCompatibility;

import java.util.ArrayList;
import java.util.List;

public final class ActionGraphComponentCatalogUsage {
    private ActionGraphComponentCatalogUsage() {
    }

    public static void main(String[] args) {
        ActionGraphComponentCatalogService catalog = ActionGraphComponentCatalogService.defaultCatalog();
        for (String module : java8LoadableModules(catalog)) {
            System.out.println(module);
        }
    }

    public static List<String> java8LoadableModules(ActionGraphComponentCatalogService catalog) {
        List<String> modules = new ArrayList<String>();
        List<ActionGraphComponent> components =
                catalog.componentsByCompatibility(ComponentCompatibility.JAVA8_CLIENT.label());
        for (ActionGraphComponent component : components) {
            modules.add(component.module());
        }
        return modules;
    }

    public static boolean canLoadInJava8(ActionGraphComponentCatalogService catalog, String module) {
        java.util.Optional<ActionGraphComponent> component = catalog.component(module);
        return component.isPresent()
                && ComponentCompatibility.JAVA8_CLIENT.label().equals(component.get().compatibility());
    }

    public static List<String> modulesForProfile(ActionGraphComponentCatalogService catalog, String profileName) {
        java.util.Optional<ActionGraphCompositionProfile> profile = catalog.profile(profileName);
        if (!profile.isPresent()) {
            return java.util.Collections.emptyList();
        }
        return profile.get().modules();
    }
}
