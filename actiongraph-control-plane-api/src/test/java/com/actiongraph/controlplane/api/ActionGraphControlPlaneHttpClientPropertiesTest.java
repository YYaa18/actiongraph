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
import java.util.Properties;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphControlPlaneHttpClientPropertiesTest {
    @Test
    void propertiesCanBuildAggregateClientFromOneBaseUrl() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> runtimePath = new AtomicReference<>();
        AtomicReference<String> runtimeToken = new AtomicReference<>();
        AtomicReference<String> runtimeSource = new AtomicReference<>();
        AtomicReference<String> runtimeRequestId = new AtomicReference<>();
        AtomicReference<String> catalogPath = new AtomicReference<>();
        AtomicReference<String> catalogSource = new AtomicReference<>();
        AtomicInteger catalogAttempts = new AtomicInteger();
        AtomicReference<String> reviewPath = new AtomicReference<>();
        AtomicReference<String> consolePath = new AtomicReference<>();

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
            catalogSource.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            if (catalogAttempts.incrementAndGet() == 1) {
                send(exchange, 503, "{\"error\":\"UNAVAILABLE\"}");
            } else {
                send(exchange, 200, "[]");
            }
        });
        server.createContext("/actiongraph/human-review/tasks/pending", exchange -> {
            reviewPath.set(exchange.getRequestURI().getPath());
            send(exchange, 200, "[]");
        });
        server.createContext("/actiongraph/console/runs", exchange -> {
            consolePath.set(exchange.getRequestURI().getPath());
            send(exchange, 200, "{\"runs\":[]}");
        });
        server.start();
        try {
            Properties properties = new Properties();
            properties.setProperty("actiongraph.control-plane.base-url", actionGraphBaseUrl(server));
            properties.setProperty("actiongraph.control-plane.shared-secret", "shared-secret");
            properties.setProperty("actiongraph.control-plane.default-header.X-Source-System", "legacy-properties");
            properties.setProperty("actiongraph.control-plane.default-header.X-Request-Id", "DEFAULT-REQ");
            properties.setProperty("actiongraph.control-plane.connect-timeout-millis", "5000");
            properties.setProperty("actiongraph.control-plane.read-timeout-millis", "30000");
            properties.setProperty("actiongraph.control-plane.max-get-retries", "1");
            properties.setProperty("actiongraph.control-plane.get-retry-backoff-millis", "0");

            ActionGraphControlPlaneHttpClient client = ActionGraphControlPlaneHttpClientProperties.build(properties);

            assertThat(client.hasRuntime()).isTrue();
            assertThat(client.hasCatalog()).isTrue();
            assertThat(client.hasHumanReview()).isTrue();
            assertThat(client.hasConsole()).isTrue();

            client.runtime().start("Prepare renewal quote for C001", null,
                    Collections.singletonMap("X-Request-Id", "REQ-1"));
            client.catalog().modules();
            client.humanReview().pendingTasks();
            client.console().runs(10, 0, null, null);

            assertThat(runtimePath.get()).isEqualTo("/actiongraph/runtime/runs");
            assertThat(runtimeToken.get()).isEqualTo("shared-secret");
            assertThat(runtimeSource.get()).isEqualTo("legacy-properties");
            assertThat(runtimeRequestId.get()).isEqualTo("REQ-1");
            assertThat(catalogPath.get()).isEqualTo("/actiongraph/components/modules");
            assertThat(catalogSource.get()).isEqualTo("legacy-properties");
            assertThat(catalogAttempts.get()).isEqualTo(2);
            assertThat(reviewPath.get()).isEqualTo("/actiongraph/human-review/tasks/pending");
            assertThat(consolePath.get()).isEqualTo("/actiongraph/console/runs");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void customPrefixAndSplitPropertiesCanEnableOneSurface() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> source = new AtomicReference<>();
        server.createContext("/gateway/runtime/runs", exchange -> {
            token.set(exchange.getRequestHeaders().getFirst("X-Legacy-Runtime-Token"));
            source.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            read(exchange);
            send(exchange, 200, "{\"runId\":\"RUN-1\"}");
        });
        server.start();
        try {
            Properties properties = new Properties();
            properties.setProperty("legacy.actiongraph.runtime.base-url", customRuntimeBaseUrl(server));
            properties.setProperty("legacy.actiongraph.runtime.shared-secret", "runtime-secret");
            properties.setProperty("legacy.actiongraph.runtime.token-header", "X-Legacy-Runtime-Token");
            properties.setProperty("legacy.actiongraph.default-header.X-Source-System", "legacy-split");

            ActionGraphControlPlaneHttpClient client = ActionGraphControlPlaneHttpClientProperties
                    .build(properties, "legacy.actiongraph");

            assertThat(client.hasRuntime()).isTrue();
            assertThat(client.hasCatalog()).isFalse();
            assertThat(client.hasHumanReview()).isFalse();
            assertThat(client.hasConsole()).isFalse();

            ControlPlaneHttpResponse response = client.runtime().start("Prepare renewal quote for C001");

            assertThat(response.successful()).isTrue();
            assertThat(token.get()).isEqualTo("runtime-secret");
            assertThat(source.get()).isEqualTo("legacy-split");
            assertThatThrownBy(client::catalog)
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("component catalog endpoint is not configured");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void invalidTimeoutPropertiesFailFast() {
        Properties notANumber = new Properties();
        notANumber.setProperty("actiongraph.control-plane.base-url", "http://127.0.0.1/actiongraph");
        notANumber.setProperty("actiongraph.control-plane.connect-timeout-millis", "slow");

        assertThatThrownBy(() -> ActionGraphControlPlaneHttpClientProperties.build(notANumber))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("connect-timeout-millis must be an integer");

        Properties negative = new Properties();
        negative.setProperty("actiongraph.control-plane.base-url", "http://127.0.0.1/actiongraph");
        negative.setProperty("actiongraph.control-plane.read-timeout-millis", "-1");

        assertThatThrownBy(() -> ActionGraphControlPlaneHttpClientProperties.build(negative))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("readTimeoutMillis must not be negative");

        Properties invalidRetries = new Properties();
        invalidRetries.setProperty("actiongraph.control-plane.base-url", "http://127.0.0.1/actiongraph");
        invalidRetries.setProperty("actiongraph.control-plane.max-get-retries", "many");

        assertThatThrownBy(() -> ActionGraphControlPlaneHttpClientProperties.build(invalidRetries))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("max-get-retries must be an integer");

        Properties negativeBackoff = new Properties();
        negativeBackoff.setProperty("actiongraph.control-plane.base-url", "http://127.0.0.1/actiongraph");
        negativeBackoff.setProperty("actiongraph.control-plane.get-retry-backoff-millis", "-1");

        assertThatThrownBy(() -> ActionGraphControlPlaneHttpClientProperties.build(negativeBackoff))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("getRetryBackoffMillis must not be negative");
    }

    private static String actionGraphBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph";
    }

    private static String customRuntimeBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/gateway/runtime";
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
