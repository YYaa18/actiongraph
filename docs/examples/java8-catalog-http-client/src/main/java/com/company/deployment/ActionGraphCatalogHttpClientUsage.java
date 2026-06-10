package com.company.deployment;

import com.actiongraph.controlplane.api.ActionGraphComponentCatalogHttpClient;
import com.actiongraph.controlplane.api.ControlPlaneHttpResponse;

import java.io.IOException;

public final class ActionGraphCatalogHttpClientUsage {
    private ActionGraphCatalogHttpClientUsage() {
    }

    public static void main(String[] args) throws Exception {
        ActionGraphComponentCatalogHttpClient client = catalogClientFromEnvironment();

        ControlPlaneHttpResponse java8Modules = client.modulesByCompatibility("java8-client");
        requireSuccessful(java8Modules);
        System.out.println(java8Modules.body());

        ControlPlaneHttpResponse profile = client.profile("java8-legacy-client");
        requireSuccessful(profile);
        System.out.println(profile.body());
    }

    public static ActionGraphComponentCatalogHttpClient catalogClientFromEnvironment() {
        String baseUrl = requireEnvironment("ACTIONGRAPH_CATALOG_URL");
        String sharedSecret = System.getenv("ACTIONGRAPH_CATALOG_TOKEN");
        return ActionGraphComponentCatalogHttpClient
                .builder(baseUrl)
                .tokenHeader(ActionGraphComponentCatalogHttpClient.DEFAULT_CATALOG_TOKEN_HEADER)
                .sharedSecret(sharedSecret)
                .connectTimeoutMillis(5000)
                .readTimeoutMillis(30000)
                .build();
    }

    public static boolean canReachCatalog(ActionGraphComponentCatalogHttpClient client) throws IOException {
        return client.catalog().successful();
    }

    public static String fetchModuleJson(ActionGraphComponentCatalogHttpClient client, String module)
            throws IOException {
        ControlPlaneHttpResponse response = client.module(module);
        requireSuccessful(response);
        return response.body();
    }

    private static void requireSuccessful(ControlPlaneHttpResponse response) throws IOException {
        if (!response.successful()) {
            throw new IOException("ActionGraph catalog request failed: HTTP "
                    + response.statusCode() + " " + response.body());
        }
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }
}
