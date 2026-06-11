package com.company.legacy;

import com.actiongraph.catalog.ActionGraphComponent;
import com.actiongraph.catalog.ActionGraphComponentCatalogService;
import com.actiongraph.catalog.ComponentCompatibility;
import com.actiongraph.controlplane.api.ActionGraphComponentCatalogHttpClient;
import com.actiongraph.controlplane.api.ActionGraphRuntimeHttpClient;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.auth.ControlPlaneTokenVerifier;
import com.actiongraph.controlplane.auth.SharedSecretTokenProtection;

import java.util.ArrayList;
import java.util.List;

public final class MavenJava8ActionGraphConsumerUsage {
    private MavenJava8ActionGraphConsumerUsage() {
    }

    public static void main(String[] args) {
        List<String> modules = java8LoadableModules();
        System.out.println(modules);
    }

    public static List<String> java8LoadableModules() {
        ActionGraphComponentCatalogService catalog = ActionGraphComponentCatalogService.defaultCatalog();
        List<ActionGraphComponent> components =
                catalog.componentsByCompatibility(ComponentCompatibility.JAVA8_CLIENT.label());
        List<String> modules = new ArrayList<String>();
        for (ActionGraphComponent component : components) {
            modules.add(component.module());
        }
        return modules;
    }

    public static ActionGraphRuntimeHttpClient runtimeClient(String runtimeUrl, String token) {
        return ActionGraphRuntimeHttpClient
                .builder(runtimeUrl)
                .sharedSecret(token)
                .defaultHeader("X-Source-System", "legacy-maven-app")
                .build();
    }

    public static ActionGraphComponentCatalogHttpClient catalogClient(String catalogUrl, String token) {
        return ActionGraphComponentCatalogHttpClient
                .builder(catalogUrl)
                .sharedSecret(token)
                .defaultHeader("X-Source-System", "legacy-maven-app")
                .build();
    }

    public static ControlPlaneErrorResponse unauthorized(String message) {
        return ControlPlaneErrorResponse.unauthorized(message);
    }

    public static boolean tokenMatches(String expectedSecret, String actualToken) {
        SharedSecretTokenProtection protection =
                new SharedSecretTokenProtection("X-ActionGraph-Runtime-Token", expectedSecret);
        return new ControlPlaneTokenVerifier().isAuthorized(protection, actualToken);
    }
}
