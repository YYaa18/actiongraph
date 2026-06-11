package com.actiongraph.controlplane.api;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphHumanReviewHttpClientTest {
    @Test
    void queriesAndDecidesReviewTasksWithJava8HttpClientShape() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> pendingMethod = new AtomicReference<>();
        AtomicReference<String> pendingPath = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> sourceSystem = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> decisionPath = new AtomicReference<>();
        AtomicReference<String> decisionBody = new AtomicReference<>();
        AtomicReference<String> callbackPath = new AtomicReference<>();
        AtomicReference<String> callbackBody = new AtomicReference<>();

        server.createContext("/actiongraph/human-review/tasks/pending", exchange -> {
            pendingMethod.set(exchange.getRequestMethod());
            pendingPath.set(exchange.getRequestURI().getPath());
            token.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Review-Token"));
            sourceSystem.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            read(exchange);
            send(exchange, 200, "[{\"runId\":\"RUN-1\"}]");
        });
        server.createContext("/actiongraph/human-review/tasks/runs", exchange -> {
            decisionPath.set(exchange.getRequestURI().getRawPath());
            decisionBody.set(read(exchange));
            send(exchange, 200, "{\"runId\":\"RUN 1\",\"decision\":\"APPROVED\"}");
        });
        server.createContext("/actiongraph/human-review/callbacks", exchange -> {
            callbackPath.set(exchange.getRequestURI().getPath());
            callbackBody.set(read(exchange));
            send(exchange, 200, "{\"runId\":\"RUN-1\",\"stageDecisionCount\":1}");
        });
        server.start();
        try {
            Map<String, String> defaultHeaders = new LinkedHashMap<>();
            defaultHeaders.put("X-Source-System", "legacy-approval");
            defaultHeaders.put("X-Request-Id", "DEFAULT-REVIEW-REQUEST");
            ActionGraphHumanReviewHttpClient client = ActionGraphHumanReviewHttpClient
                    .builder(baseTaskUrl(server))
                    .sharedSecret("review-secret")
                    .defaultHeaders(defaultHeaders)
                    .build();

            ControlPlaneHttpResponse pending = client.pendingTasks(
                    Collections.singletonMap("X-Request-Id", "REQ-REVIEW-1"));
            ControlPlaneHttpResponse decided = client.decide(
                    "RUN 1", "claim.approval/request", 0, "APPROVED", "checker", "同意 \"支付\"\n复核");
            ControlPlaneHttpResponse callback = client.callback(
                    "RUN-1", "claim.approval.request", 0, "APPROVED", "checker", "approved");

            assertThat(pendingMethod.get()).isEqualTo("GET");
            assertThat(pendingPath.get()).isEqualTo("/actiongraph/human-review/tasks/pending");
            assertThat(token.get()).isEqualTo("review-secret");
            assertThat(sourceSystem.get()).isEqualTo("legacy-approval");
            assertThat(requestId.get()).isEqualTo("REQ-REVIEW-1");
            assertThat(pending.body()).contains("RUN-1");
            assertThat(decisionPath.get())
                    .isEqualTo("/actiongraph/human-review/tasks/runs/RUN%201/actions/claim.approval%2Frequest/decision");
            assertThat(decisionBody.get())
                    .contains("\"expectedStageIndex\":0")
                    .contains("\"decision\":\"APPROVED\"")
                    .contains("\"reviewer\":\"checker\"")
                    .contains("\\\"支付\\\"")
                    .contains("\\n");
            assertThat(decided.successful()).isTrue();
            assertThat(callbackPath.get()).isEqualTo("/actiongraph/human-review/callbacks");
            assertThat(callbackBody.get())
                    .contains("\"runId\":\"RUN-1\"")
                    .contains("\"actionId\":\"claim.approval.request\"");
            assertThat(callback.statusCode()).isEqualTo(200);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsTaskViewsAndPreservesErrorBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> runPath = new AtomicReference<>();
        AtomicReference<String> taskPath = new AtomicReference<>();
        server.createContext("/custom/review/tasks/runs", exchange -> {
            String path = exchange.getRequestURI().getRawPath();
            if (path.endsWith("/actions/action%201")) {
                taskPath.set(path);
                send(exchange, 404, "{\"error\":\"NOT_FOUND\",\"message\":\"missing\"}");
            } else {
                runPath.set(path);
                send(exchange, 200, "[{\"runId\":\"RUN 1\"}]");
            }
        });
        server.start();
        try {
            ActionGraphHumanReviewHttpClient client = ActionGraphHumanReviewHttpClient
                    .builder(customTaskUrl(server) + "/")
                    .callbackApiBaseUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/custom/callbacks/")
                    .build();

            ControlPlaneHttpResponse runTasks = client.tasksForRun("RUN 1");
            ControlPlaneHttpResponse missing = client.task("RUN 1", "action 1");

            assertThat(runPath.get()).isEqualTo("/custom/review/tasks/runs/RUN%201");
            assertThat(runTasks.body()).contains("RUN 1");
            assertThat(taskPath.get()).isEqualTo("/custom/review/tasks/runs/RUN%201/actions/action%201");
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(missing.successful()).isFalse();
            assertThat(missing.body()).contains("NOT_FOUND");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesTransientGetFailuresButNotPostDecisions() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger pendingAttempts = new AtomicInteger();
        AtomicInteger decisionAttempts = new AtomicInteger();
        server.createContext("/actiongraph/human-review/tasks/pending", exchange -> {
            if (pendingAttempts.incrementAndGet() == 1) {
                send(exchange, 502, "{\"error\":\"BAD_GATEWAY\"}");
            } else {
                send(exchange, 200, "[{\"runId\":\"RUN-1\"}]");
            }
        });
        server.createContext("/actiongraph/human-review/tasks/runs", exchange -> {
            decisionAttempts.incrementAndGet();
            read(exchange);
            send(exchange, 503, "{\"error\":\"UNAVAILABLE\"}");
        });
        server.start();
        try {
            ActionGraphHumanReviewHttpClient client = ActionGraphHumanReviewHttpClient
                    .builder(baseTaskUrl(server))
                    .maxGetRetries(3)
                    .getRetryBackoffMillis(0)
                    .build();

            ControlPlaneHttpResponse pending = client.pendingTasks();
            ControlPlaneHttpResponse decision = client.decide(
                    "RUN-1", "claim.approval.request", 0, "APPROVED", "checker", "approved");

            assertThat(pending.successful()).isTrue();
            assertThat(pendingAttempts.get()).isEqualTo(2);
            assertThat(decision.statusCode()).isEqualTo(503);
            assertThat(decisionAttempts.get()).isEqualTo(1);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatesRequiredFieldsBeforeSending() {
        assertThatThrownBy(() -> ActionGraphHumanReviewHttpClient.builder(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("taskApiBaseUrl");
        assertThatThrownBy(() -> ActionGraphHumanReviewHttpClient.builder("http://localhost/tasks")
                .callbackApiBaseUrl(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("callbackApiBaseUrl");
        assertThatThrownBy(() -> ActionGraphHumanReviewHttpClient.builder("http://localhost/tasks")
                .defaultHeader(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default header name");
        assertThatThrownBy(() -> ActionGraphHumanReviewHttpClient.builder("http://localhost/tasks").build()
                .pendingTasks(Collections.singletonMap(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request header name");
        assertThatThrownBy(() -> ActionGraphHumanReviewHttpClient.builder("http://localhost/tasks").build()
                .decide("RUN-1", "action", 0, " ", "checker", "comment"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("decision");
        assertThatThrownBy(() -> ActionGraphHumanReviewHttpClient.builder("http://localhost/tasks")
                .maxGetRetries(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxGetRetries");
        assertThatThrownBy(() -> ActionGraphHumanReviewHttpClient.builder("http://localhost/tasks")
                .getRetryBackoffMillis(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("getRetryBackoffMillis");
    }

    private static String baseTaskUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/human-review/tasks";
    }

    private static String customTaskUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/custom/review/tasks";
    }

    private static String read(HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int read;
        while ((read = exchange.getRequestBody().read(bytes)) != -1) {
            buffer.write(bytes, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void send(HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json; charset=UTF-8");
        exchange.sendResponseHeaders(status, bytes.length);
        OutputStream responseBody = exchange.getResponseBody();
        try {
            responseBody.write(bytes);
        } finally {
            responseBody.close();
        }
    }
}
