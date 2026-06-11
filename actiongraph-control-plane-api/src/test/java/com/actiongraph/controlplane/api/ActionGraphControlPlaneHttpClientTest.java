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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphControlPlaneHttpClientTest {
    @Test
    void aggregateBaseUrlConfiguresAllControlPlaneSurfaces() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> runtimePath = new AtomicReference<>();
        AtomicReference<String> runtimeToken = new AtomicReference<>();
        AtomicReference<String> runtimeSource = new AtomicReference<>();
        AtomicReference<String> runtimeRequestId = new AtomicReference<>();
        AtomicReference<String> catalogPath = new AtomicReference<>();
        AtomicReference<String> catalogToken = new AtomicReference<>();
        AtomicReference<String> reviewPath = new AtomicReference<>();
        AtomicReference<String> reviewToken = new AtomicReference<>();
        AtomicReference<String> callbackPath = new AtomicReference<>();
        AtomicReference<String> consolePath = new AtomicReference<>();
        AtomicReference<String> consoleToken = new AtomicReference<>();
        AtomicReference<String> consoleQuery = new AtomicReference<>();

        server.createContext("/actiongraph/runtime/runs", exchange -> {
            runtimePath.set(exchange.getRequestURI().getPath());
            runtimeToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Runtime-Token"));
            runtimeSource.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            runtimeRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            read(exchange);
            send(exchange, 200, "{\"runId\":\"RUN-1\"}");
        });
        server.createContext("/actiongraph/components/modules", exchange -> {
            catalogPath.set(exchange.getRequestURI().getPath());
            catalogToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Catalog-Token"));
            send(exchange, 200, "[]");
        });
        server.createContext("/actiongraph/human-review/tasks/pending", exchange -> {
            reviewPath.set(exchange.getRequestURI().getPath());
            reviewToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Review-Token"));
            send(exchange, 200, "[]");
        });
        server.createContext("/actiongraph/human-review/callbacks", exchange -> {
            callbackPath.set(exchange.getRequestURI().getPath());
            read(exchange);
            send(exchange, 200, "{\"stageDecisionCount\":1}");
        });
        server.createContext("/actiongraph/console/runs", exchange -> {
            consolePath.set(exchange.getRequestURI().getPath());
            consoleQuery.set(exchange.getRequestURI().getRawQuery());
            consoleToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Console-Token"));
            send(exchange, 200, "{\"runs\":[]}");
        });
        server.start();
        try {
            ActionGraphControlPlaneHttpClient client = ActionGraphControlPlaneHttpClient
                    .builder(actionGraphBaseUrl(server) + "/")
                    .sharedSecret("shared-secret")
                    .defaultHeader("X-Source-System", "legacy-gateway")
                    .defaultHeader("X-Request-Id", "DEFAULT-REQ")
                    .build();

            assertThat(client.hasRuntime()).isTrue();
            assertThat(client.hasCatalog()).isTrue();
            assertThat(client.hasHumanReview()).isTrue();
            assertThat(client.hasConsole()).isTrue();

            client.runtime().start("Prepare renewal quote for C001", null,
                    Collections.singletonMap("X-Request-Id", "REQ-1"));
            client.catalog().modules();
            client.humanReview().pendingTasks();
            client.humanReview().callback("RUN-1", "quote.approval.request", 0,
                    "APPROVED", "checker", "approved");
            client.console().runs(25, 10, "COMPLETED", Boolean.TRUE);

            assertThat(runtimePath.get()).isEqualTo("/actiongraph/runtime/runs");
            assertThat(runtimeToken.get()).isEqualTo("shared-secret");
            assertThat(runtimeSource.get()).isEqualTo("legacy-gateway");
            assertThat(runtimeRequestId.get()).isEqualTo("REQ-1");
            assertThat(catalogPath.get()).isEqualTo("/actiongraph/components/modules");
            assertThat(catalogToken.get()).isEqualTo("shared-secret");
            assertThat(reviewPath.get()).isEqualTo("/actiongraph/human-review/tasks/pending");
            assertThat(reviewToken.get()).isEqualTo("shared-secret");
            assertThat(callbackPath.get()).isEqualTo("/actiongraph/human-review/callbacks");
            assertThat(consolePath.get()).isEqualTo("/actiongraph/console/runs");
            assertThat(consoleQuery.get()).isEqualTo("limit=25&offset=10&status=COMPLETED&auditComplete=true");
            assertThat(consoleToken.get()).isEqualTo("shared-secret");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void splitConfigurationKeepsUnconfiguredSurfacesUnavailable() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> runtimeToken = new AtomicReference<>();
        server.createContext("/custom/runtime/runs", exchange -> {
            runtimeToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Runtime-Token"));
            read(exchange);
            send(exchange, 200, "{\"runId\":\"RUN-1\"}");
        });
        server.start();
        try {
            ActionGraphControlPlaneHttpClient client = ActionGraphControlPlaneHttpClient
                    .builder()
                    .runtimeApiBaseUrl(customRuntimeBaseUrl(server))
                    .runtimeSharedSecret("runtime-secret")
                    .build();

            assertThat(client.hasRuntime()).isTrue();
            assertThat(client.hasCatalog()).isFalse();
            assertThat(client.hasHumanReview()).isFalse();
            assertThat(client.hasConsole()).isFalse();

            ControlPlaneHttpResponse response = client.runtime().start("Prepare renewal quote for C001");

            assertThat(response.successful()).isTrue();
            assertThat(runtimeToken.get()).isEqualTo("runtime-secret");
            assertThatThrownBy(client::catalog)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("component catalog endpoint is not configured");
            assertThatThrownBy(client::humanReview)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("human review endpoint is not configured");
            assertThatThrownBy(client::console)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("console endpoint is not configured");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatesEndpointSelectionBeforeBuild() {
        assertThatThrownBy(() -> ActionGraphControlPlaneHttpClient.builder().build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("At least one control-plane endpoint base URL");
        assertThatThrownBy(() -> ActionGraphControlPlaneHttpClient.builder()
                .reviewCallbackApiBaseUrl("http://127.0.0.1/actiongraph/human-review/callbacks")
                .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reviewTaskApiBaseUrl");
        assertThatThrownBy(() -> ActionGraphControlPlaneHttpClient.builder("http://127.0.0.1/actiongraph")
                .defaultHeader(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default header name");
    }

    private static String actionGraphBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph";
    }

    private static String customRuntimeBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/custom/runtime";
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
