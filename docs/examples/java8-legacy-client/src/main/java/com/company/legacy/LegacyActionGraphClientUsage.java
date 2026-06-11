package com.company.legacy;

import com.actiongraph.controlplane.api.ActionGraphRuntimeHttpClient;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.api.ControlPlaneHttpResponse;
import com.actiongraph.controlplane.auth.ControlPlaneTokenVerifier;
import com.actiongraph.controlplane.auth.SharedSecretTokenProperties;
import com.actiongraph.controlplane.auth.SharedSecretTokenProtection;
import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

public final class LegacyActionGraphClientUsage {
    private LegacyActionGraphClientUsage() {
    }

    public static void main(String[] args) throws Exception {
        ActionGraphRuntimeHttpClient client = runtimeClientFromEnvironment();

        Map<String, String> known = new HashMap<String, String>();
        known.put("customerId", "C001");
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("X-Request-Id", environmentOrDefault("ACTIONGRAPH_REQUEST_ID", "REQ-LOCAL-1"));

        ControlPlaneHttpResponse response = client.start("Prepare renewal quote for C001", known, requestHeaders);
        if (!response.successful()) {
            throw new IOException("ActionGraph request failed: HTTP "
                    + response.statusCode() + " " + response.body());
        }
        System.out.println(response.body());
    }

    public static ActionGraphRuntimeHttpClient runtimeClientFromEnvironment() {
        String baseUrl = requireEnvironment("ACTIONGRAPH_RUNTIME_URL");
        String sharedSecret = System.getenv("ACTIONGRAPH_RUNTIME_TOKEN");
        return ActionGraphRuntimeHttpClient
                .builder(baseUrl)
                .tokenHeader(ActionGraphRuntimeHttpClient.DEFAULT_RUNTIME_TOKEN_HEADER)
                .sharedSecret(sharedSecret)
                .defaultHeader("X-Source-System", environmentOrDefault("ACTIONGRAPH_SOURCE_SYSTEM", "legacy-crm"))
                .connectTimeoutMillis(5000)
                .readTimeoutMillis(30000)
                .build();
    }

    public static void verifyInboundGatewayToken(final Map<String, String> headers) {
        SharedSecretTokenProperties properties = new SharedSecretTokenProperties() {
            public String getTokenHeader() {
                return "X-ActionGraph-Runtime-Token";
            }

            public String getSharedSecret() {
                return System.getenv("ACTIONGRAPH_RUNTIME_TOKEN");
            }
        };

        new ControlPlaneTokenVerifier().verify(properties, new Function<String, String>() {
            public String apply(String headerName) {
                return headers.get(headerName);
            }
        }, "ActionGraph runtime token is missing or invalid");
    }

    public static ControlPlaneErrorResponse mapUnauthorized(UnauthorizedControlPlaneAccessException exception) {
        return ControlPlaneErrorResponse.unauthorized(exception.getMessage());
    }

    public static boolean tokenMatches(String expectedSecret, String actualToken) {
        SharedSecretTokenProtection protection =
                new SharedSecretTokenProtection("X-ActionGraph-Runtime-Token", expectedSecret);
        return new ControlPlaneTokenVerifier().isAuthorized(protection, actualToken);
    }

    private static String requireEnvironment(String name) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalStateException(name + " must be configured");
        }
        return value;
    }

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}
