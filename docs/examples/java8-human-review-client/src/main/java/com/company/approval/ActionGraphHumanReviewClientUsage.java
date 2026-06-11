package com.company.approval;

import com.actiongraph.controlplane.api.ActionGraphHumanReviewHttpClient;
import com.actiongraph.controlplane.api.ControlPlaneHttpResponse;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public final class ActionGraphHumanReviewClientUsage {
    private ActionGraphHumanReviewClientUsage() {
    }

    public static void main(String[] args) throws Exception {
        ActionGraphHumanReviewHttpClient client = reviewClientFromEnvironment();
        Map<String, String> requestHeaders = new HashMap<String, String>();
        requestHeaders.put("X-Request-Id", environmentOrDefault("ACTIONGRAPH_REQUEST_ID", "REQ-REVIEW-LOCAL-1"));

        ControlPlaneHttpResponse pending = client.pendingTasks(requestHeaders);
        requireSuccessful(pending);
        System.out.println(pending.body());
    }

    public static ActionGraphHumanReviewHttpClient reviewClientFromEnvironment() {
        String taskApiBaseUrl = requireEnvironment("ACTIONGRAPH_REVIEW_TASKS_URL");
        String callbackApiBaseUrl = System.getenv("ACTIONGRAPH_REVIEW_CALLBACK_URL");
        String sharedSecret = System.getenv("ACTIONGRAPH_REVIEW_TOKEN");

        ActionGraphHumanReviewHttpClient.Builder builder = ActionGraphHumanReviewHttpClient
                .builder(taskApiBaseUrl)
                .tokenHeader(ActionGraphHumanReviewHttpClient.DEFAULT_REVIEW_TOKEN_HEADER)
                .sharedSecret(sharedSecret)
                .defaultHeader("X-Source-System", environmentOrDefault("ACTIONGRAPH_SOURCE_SYSTEM", "legacy-approval"))
                .connectTimeoutMillis(5000)
                .readTimeoutMillis(30000);
        if (callbackApiBaseUrl != null && !callbackApiBaseUrl.trim().isEmpty()) {
            builder.callbackApiBaseUrl(callbackApiBaseUrl);
        }
        return builder.build();
    }

    public static ControlPlaneHttpResponse approveCurrentStage(
            ActionGraphHumanReviewHttpClient client,
            String runId,
            String actionId,
            int expectedStageIndex,
            String reviewer
    ) throws IOException {
        return client.decide(runId, actionId, Integer.valueOf(expectedStageIndex),
                "APPROVED", reviewer, "Approved by legacy approval portal");
    }

    public static ControlPlaneHttpResponse denyCurrentStage(
            ActionGraphHumanReviewHttpClient client,
            String runId,
            String actionId,
            int expectedStageIndex,
            String reviewer,
            String reason
    ) throws IOException {
        return client.decide(runId, actionId, Integer.valueOf(expectedStageIndex),
                "DENIED", reviewer, reason);
    }

    public static ControlPlaneHttpResponse replayApprovalCallback(
            ActionGraphHumanReviewHttpClient client,
            String runId,
            String actionId,
            int expectedStageIndex,
            String decision,
            String reviewer,
            String comment
    ) throws IOException {
        return client.callback(runId, actionId, expectedStageIndex, decision, reviewer, comment);
    }

    private static void requireSuccessful(ControlPlaneHttpResponse response) throws IOException {
        if (!response.successful()) {
            throw new IOException("ActionGraph human-review request failed: HTTP "
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
