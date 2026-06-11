package com.company.controlplane;

import com.actiongraph.controlplane.api.ActionGraphControlPlaneHttpClient;
import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;
import com.actiongraph.controlplane.api.ControlPlaneHttpResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ActionGraphControlPlaneClientUsage {
    private ActionGraphControlPlaneClientUsage() {
    }

    public static void main(String[] args) throws Exception {
        ActionGraphControlPlaneHttpClient client = aggregateClientFromEnvironment();
        Map<String, String> requestHeaders = requestHeadersFromEnvironment();

        ControlPlaneHttpResponse catalog = client.catalog().profilesForModule(
                "actiongraph-control-plane-api", requestHeaders);
        requireSuccessful("component catalog", catalog);
        System.out.println(catalog.body());

        ControlPlaneHttpResponse run = client.runtime().start(
                "Prepare renewal quote for C001", knownCustomer("C001"), requestHeaders);
        requireSuccessful("runtime start", run);
        System.out.println(run.body());
    }

    public static ActionGraphControlPlaneHttpClient aggregateClientFromEnvironment() {
        return ActionGraphControlPlaneHttpClient
                .builder(requireEnvironment("ACTIONGRAPH_BASE_URL"))
                .sharedSecret(System.getenv("ACTIONGRAPH_CONTROL_PLANE_TOKEN"))
                .defaultHeader("X-Source-System", environmentOrDefault("ACTIONGRAPH_SOURCE_SYSTEM", "legacy-core"))
                .connectTimeoutMillis(5000)
                .readTimeoutMillis(30000)
                .build();
    }

    public static ActionGraphControlPlaneHttpClient splitClientFromEnvironment() {
        return ActionGraphControlPlaneHttpClient
                .builder()
                .runtimeApiBaseUrl(requireEnvironment("ACTIONGRAPH_RUNTIME_URL"))
                .catalogApiBaseUrl(requireEnvironment("ACTIONGRAPH_CATALOG_URL"))
                .reviewTaskApiBaseUrl(requireEnvironment("ACTIONGRAPH_REVIEW_TASKS_URL"))
                .reviewCallbackApiBaseUrl(System.getenv("ACTIONGRAPH_REVIEW_CALLBACK_URL"))
                .consoleApiBaseUrl(requireEnvironment("ACTIONGRAPH_CONSOLE_URL"))
                .runtimeSharedSecret(System.getenv("ACTIONGRAPH_RUNTIME_TOKEN"))
                .catalogSharedSecret(System.getenv("ACTIONGRAPH_CATALOG_TOKEN"))
                .reviewSharedSecret(System.getenv("ACTIONGRAPH_REVIEW_TOKEN"))
                .consoleSharedSecret(System.getenv("ACTIONGRAPH_CONSOLE_TOKEN"))
                .defaultHeader("X-Source-System", environmentOrDefault("ACTIONGRAPH_SOURCE_SYSTEM", "legacy-core"))
                .build();
    }

    public static boolean resumeIfClaimable(
            ActionGraphControlPlaneHttpClient client,
            String runId,
            Map<String, String> requestHeaders
    ) throws IOException {
        ControlPlaneHttpResponse response = client.runtime().resume(runId, requestHeaders);
        if (response.hasError(ControlPlaneErrorResponse.NOT_CLAIMABLE)) {
            return false;
        }
        requireSuccessful("runtime resume", response);
        return true;
    }

    public static ControlPlaneHttpResponse approveCurrentStage(
            ActionGraphControlPlaneHttpClient client,
            String runId,
            String actionId,
            int expectedStageIndex,
            String reviewer,
            Map<String, String> requestHeaders
    ) throws IOException {
        return client.humanReview().decide(runId, actionId, Integer.valueOf(expectedStageIndex),
                "APPROVED", reviewer, "Approved by legacy approval portal", requestHeaders);
    }

    public static ControlPlaneHttpResponse exportTraceJsonl(
            ActionGraphControlPlaneHttpClient client,
            String runId,
            Map<String, String> requestHeaders
    ) throws IOException {
        return client.console().traceJsonl(runId, requestHeaders);
    }

    private static Map<String, String> knownCustomer(String customerId) {
        Map<String, String> known = new HashMap<String, String>();
        known.put("customerId", customerId);
        return known;
    }

    private static Map<String, String> requestHeadersFromEnvironment() {
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("X-Request-Id", environmentOrDefault("ACTIONGRAPH_REQUEST_ID", "REQ-LOCAL-1"));
        return requestHeaders;
    }

    private static void requireSuccessful(String operation, ControlPlaneHttpResponse response) throws IOException {
        if (!response.successful()) {
            throw new IOException("ActionGraph " + operation + " failed: HTTP "
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

    private static String environmentOrDefault(String name, String defaultValue) {
        String value = System.getenv(name);
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value;
    }
}
