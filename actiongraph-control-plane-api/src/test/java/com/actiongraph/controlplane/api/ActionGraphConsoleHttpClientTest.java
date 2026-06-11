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

class ActionGraphConsoleHttpClientTest {
    @Test
    void queriesRunsWithJava8HttpClientShape() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> query = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> sourceSystem = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/actiongraph/console/runs", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            query.set(exchange.getRequestURI().getRawQuery());
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            token.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Console-Token"));
            sourceSystem.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            requestBody.set(read(exchange));
            send(exchange, 200, "{\"runs\":[{\"runId\":\"RUN-1\"}]}");
        });
        server.start();
        try {
            Map<String, String> defaultHeaders = new LinkedHashMap<>();
            defaultHeaders.put("X-Source-System", "legacy-audit");
            defaultHeaders.put("X-Request-Id", "DEFAULT-CONSOLE-REQUEST");
            ActionGraphConsoleHttpClient client = ActionGraphConsoleHttpClient
                    .builder(baseUrl(server))
                    .sharedSecret("console-secret")
                    .defaultHeaders(defaultHeaders)
                    .build();

            ControlPlaneHttpResponse response = client.runs(
                    25, 10, "SUSPENDED_PENDING_REVIEW", Boolean.FALSE,
                    Collections.singletonMap("X-Request-Id", "REQ-CONSOLE-1"));

            assertThat(method.get()).isEqualTo("GET");
            assertThat(path.get()).isEqualTo("/actiongraph/console/runs");
            assertThat(query.get()).isEqualTo("limit=25&offset=10&status=SUSPENDED_PENDING_REVIEW&auditComplete=false");
            assertThat(accept.get()).isEqualTo("application/json");
            assertThat(token.get()).isEqualTo("console-secret");
            assertThat(sourceSystem.get()).isEqualTo("legacy-audit");
            assertThat(requestId.get()).isEqualTo("REQ-CONSOLE-1");
            assertThat(requestBody.get()).isEmpty();
            assertThat(response.successful()).isTrue();
            assertThat(response.body()).contains("RUN-1");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsRunTraceAndExportsAuditEvidence() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> runPath = new AtomicReference<>();
        AtomicReference<String> tracePath = new AtomicReference<>();
        AtomicReference<String> runsCsvPath = new AtomicReference<>();
        AtomicReference<String> traceCsvPath = new AtomicReference<>();
        AtomicReference<String> traceJsonlPath = new AtomicReference<>();
        AtomicReference<String> csvAccept = new AtomicReference<>();
        AtomicReference<String> jsonlAccept = new AtomicReference<>();
        server.createContext("/custom/console/runs", exchange -> {
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath.endsWith("/trace/export.jsonl")) {
                traceJsonlPath.set(rawPath);
                jsonlAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
                send(exchange, 200, "{\"seq\":1}\n");
            } else if (rawPath.endsWith("/trace/export.csv")) {
                traceCsvPath.set(rawPath);
                csvAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
                send(exchange, 200, "seq,type\n1,RUN_STARTED\n");
            } else if (rawPath.endsWith("/trace")) {
                tracePath.set(rawPath);
                send(exchange, 200, "{\"events\":[]}");
            } else if (rawPath.endsWith("/export.csv")) {
                runsCsvPath.set(rawPath);
                csvAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
                send(exchange, 200, "runId,status\nRUN-1,COMPLETED\n");
            } else {
                runPath.set(rawPath);
                send(exchange, 404, "{\"error\":\"NOT_FOUND\",\"message\":\"missing\"}");
            }
        });
        server.start();
        try {
            ActionGraphConsoleHttpClient client = ActionGraphConsoleHttpClient
                    .builder(customBaseUrl(server) + "/")
                    .build();

            ControlPlaneHttpResponse missing = client.run("RUN 1");
            ControlPlaneHttpResponse trace = client.trace("RUN 1");
            ControlPlaneHttpResponse runsCsv = client.runsCsv(10, 0, "COMPLETED", Boolean.TRUE);
            ControlPlaneHttpResponse traceCsv = client.traceCsv("RUN 1");
            ControlPlaneHttpResponse traceJsonl = client.traceJsonl("RUN 1");

            assertThat(runPath.get()).isEqualTo("/custom/console/runs/RUN%201");
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(missing.body()).contains("NOT_FOUND");
            assertThat(tracePath.get()).isEqualTo("/custom/console/runs/RUN%201/trace");
            assertThat(trace.body()).contains("events");
            assertThat(runsCsvPath.get()).isEqualTo("/custom/console/runs/export.csv");
            assertThat(runsCsv.body()).contains("runId,status");
            assertThat(traceCsvPath.get()).isEqualTo("/custom/console/runs/RUN%201/trace/export.csv");
            assertThat(traceCsv.body()).contains("RUN_STARTED");
            assertThat(traceJsonlPath.get()).isEqualTo("/custom/console/runs/RUN%201/trace/export.jsonl");
            assertThat(traceJsonl.body()).contains("\"seq\":1");
            assertThat(csvAccept.get()).isEqualTo("text/csv");
            assertThat(jsonlAccept.get()).isEqualTo("application/x-ndjson");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesTransientGetFailuresForAuditQueries() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/actiongraph/console/runs/RUN-1/trace/export.jsonl", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                send(exchange, 504, "{\"error\":\"GATEWAY_TIMEOUT\"}");
            } else {
                send(exchange, 200, "{\"seq\":1}\n");
            }
        });
        server.start();
        try {
            ActionGraphConsoleHttpClient client = ActionGraphConsoleHttpClient
                    .builder(baseUrl(server))
                    .maxGetRetries(2)
                    .getRetryBackoffMillis(0)
                    .build();

            ControlPlaneHttpResponse response = client.traceJsonl("RUN-1");

            assertThat(response.successful()).isTrue();
            assertThat(response.body()).contains("\"seq\":1");
            assertThat(attempts.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatesRequiredFieldsBeforeSending() {
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("consoleApiBaseUrl");
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder("http://localhost/console")
                .defaultHeader(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default header name");
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder("http://localhost/console").build()
                .runs(0, 0, "COMPLETED", Boolean.TRUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("limit");
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder("http://localhost/console").build()
                .runs(10, -1, "COMPLETED", Boolean.TRUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("offset");
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder("http://localhost/console").build()
                .runs(10, 0, " ", Boolean.TRUE))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("status");
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder("http://localhost/console").build()
                .get("/runs", Collections.singletonMap(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request header name");
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder("http://localhost/console")
                .maxGetRetries(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxGetRetries");
        assertThatThrownBy(() -> ActionGraphConsoleHttpClient.builder("http://localhost/console")
                .getRetryBackoffMillis(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("getRetryBackoffMillis");
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/console";
    }

    private static String customBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/custom/console";
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
