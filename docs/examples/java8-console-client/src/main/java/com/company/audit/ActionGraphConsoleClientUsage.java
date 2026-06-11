package com.company.audit;

import com.actiongraph.controlplane.api.ActionGraphConsoleHttpClient;
import com.actiongraph.controlplane.api.ControlPlaneHttpResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ActionGraphConsoleClientUsage {
    private ActionGraphConsoleClientUsage() {
    }

    public static void main(String[] args) throws Exception {
        ActionGraphConsoleHttpClient client = consoleClientFromEnvironment();
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("X-Request-Id", environmentOrDefault("ACTIONGRAPH_REQUEST_ID", "REQ-CONSOLE-LOCAL-1"));

        ControlPlaneHttpResponse runs = client.runs(50, 0, null, null, requestHeaders);
        requireSuccessful(runs);
        System.out.println(runs.body());
    }

    public static ActionGraphConsoleHttpClient consoleClientFromEnvironment() {
        String baseUrl = requireEnvironment("ACTIONGRAPH_CONSOLE_URL");
        String sharedSecret = System.getenv("ACTIONGRAPH_CONSOLE_TOKEN");
        return ActionGraphConsoleHttpClient
                .builder(baseUrl)
                .tokenHeader(ActionGraphConsoleHttpClient.DEFAULT_CONSOLE_TOKEN_HEADER)
                .sharedSecret(sharedSecret)
                .defaultHeader("X-Source-System", environmentOrDefault("ACTIONGRAPH_SOURCE_SYSTEM", "legacy-audit"))
                .connectTimeoutMillis(5000)
                .readTimeoutMillis(30000)
                .build();
    }

    public static ControlPlaneHttpResponse completedRunsCsv(ActionGraphConsoleHttpClient client)
            throws IOException {
        return client.runsCsv(200, 0, "COMPLETED", Boolean.TRUE);
    }

    public static ControlPlaneHttpResponse runTraceJson(ActionGraphConsoleHttpClient client, String runId)
            throws IOException {
        return client.trace(runId);
    }

    public static ControlPlaneHttpResponse runTraceCsv(ActionGraphConsoleHttpClient client, String runId)
            throws IOException {
        return client.traceCsv(runId);
    }

    public static ControlPlaneHttpResponse runTraceJsonl(ActionGraphConsoleHttpClient client, String runId)
            throws IOException {
        return client.traceJsonl(runId);
    }

    private static void requireSuccessful(ControlPlaneHttpResponse response) throws IOException {
        if (!response.successful()) {
            throw new IOException("ActionGraph console request failed: HTTP "
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
