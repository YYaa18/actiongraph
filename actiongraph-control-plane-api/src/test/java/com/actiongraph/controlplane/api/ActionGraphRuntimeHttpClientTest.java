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
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ActionGraphRuntimeHttpClientTest {
    @Test
    void startsRunWithJava8HttpClientShape() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> sourceSystem = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/actiongraph/runtime/runs", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            token.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Runtime-Token"));
            sourceSystem.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            requestBody.set(read(exchange));
            send(exchange, 200, "{\"disposition\":\"RUN_STARTED\"}");
        });
        server.start();
        try {
            ActionGraphRuntimeHttpClient client = ActionGraphRuntimeHttpClient
                    .builder(baseUrl(server))
                    .sharedSecret("secret")
                    .defaultHeader("X-Source-System", "legacy-crm")
                    .defaultHeader("X-Request-Id", "DEFAULT-REQUEST")
                    .build();
            Map<String, String> known = new LinkedHashMap<>();
            known.put("customerId", "C001");
            Map<String, String> requestHeaders = new LinkedHashMap<>();
            requestHeaders.put("X-Request-Id", "REQ-20260611-0001");

            ControlPlaneHttpResponse response = client.start("帮客户 C001 生成续约报价", known, requestHeaders);

            assertThat(method.get()).isEqualTo("POST");
            assertThat(path.get()).isEqualTo("/actiongraph/runtime/runs");
            assertThat(token.get()).isEqualTo("secret");
            assertThat(sourceSystem.get()).isEqualTo("legacy-crm");
            assertThat(requestId.get()).isEqualTo("REQ-20260611-0001");
            assertThat(requestBody.get())
                    .contains("\"input\":\"帮客户 C001 生成续约报价\"")
                    .contains("\"knownParameters\":{\"customerId\":\"C001\"}");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.successful()).isTrue();
            assertThat(response.body()).isEqualTo("{\"disposition\":\"RUN_STARTED\"}");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void runtimeHttpClientCanBeUsedThroughJava8GatewayInterface() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/actiongraph/runtime/interpret", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            read(exchange);
            send(exchange, 200, "{\"ready\":true}");
        });
        server.start();
        try {
            ActionGraphRuntimeGateway gateway = ActionGraphRuntimeHttpClient.builder(baseUrl(server)).build();

            ControlPlaneHttpResponse response = gateway.interpret("finish");

            assertThat(path.get()).isEqualTo("/actiongraph/runtime/interpret");
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.body()).contains("\"ready\":true");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void resumesRunAndPreservesErrorBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> path = new AtomicReference<>();
        server.createContext("/actiongraph/runtime/runs/RUN-1/resume", exchange -> {
            path.set(exchange.getRequestURI().getPath());
            read(exchange);
            send(exchange, 409, "{\"error\":\"NOT_CLAIMABLE\",\"message\":\"already claimed\"}");
        });
        server.start();
        try {
            ActionGraphRuntimeHttpClient client = ActionGraphRuntimeHttpClient.builder(baseUrl(server)).build();

            ControlPlaneHttpResponse response = client.resume("RUN-1");

            assertThat(path.get()).isEqualTo("/actiongraph/runtime/runs/RUN-1/resume");
            assertThat(response.statusCode()).isEqualTo(409);
            assertThat(response.successful()).isFalse();
            assertThat(response.body()).contains("NOT_CLAIMABLE");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void escapesJsonWithoutExternalDependencies() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/actiongraph/runtime/interpret", exchange -> {
            requestBody.set(read(exchange));
            send(exchange, 200, "{}");
        });
        server.start();
        try {
            ActionGraphRuntimeHttpClient client = ActionGraphRuntimeHttpClient.builder(baseUrl(server)).build();

            client.interpret("客户 \"C001\"\n续约", Collections.singletonMap("note", "A\\B"));

            assertThat(requestBody.get())
                    .contains("\\\"C001\\\"")
                    .contains("\\n")
                    .contains("\"note\":\"A\\\\B\"");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatesRequiredFieldsBeforeSending() {
        assertThatThrownBy(() -> ActionGraphRuntimeHttpClient.builder(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runtimeApiBaseUrl");
        assertThatThrownBy(() -> ActionGraphRuntimeHttpClient.builder("http://localhost").build().start(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("input");
        assertThatThrownBy(() -> ActionGraphRuntimeHttpClient.builder("http://localhost").defaultHeader(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default header name");
        assertThatThrownBy(() -> ActionGraphRuntimeHttpClient.builder("http://localhost").build()
                .post("/runs", "{}", Collections.singletonMap(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request header name");
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/runtime";
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
