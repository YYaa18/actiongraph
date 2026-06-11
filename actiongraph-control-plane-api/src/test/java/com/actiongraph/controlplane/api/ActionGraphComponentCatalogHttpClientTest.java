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

class ActionGraphComponentCatalogHttpClientTest {
    @Test
    void readsModulesWithJava8HttpClientShape() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> method = new AtomicReference<>();
        AtomicReference<String> path = new AtomicReference<>();
        AtomicReference<String> accept = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        AtomicReference<String> sourceSystem = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> requestBody = new AtomicReference<>();
        server.createContext("/actiongraph/components/modules", exchange -> {
            method.set(exchange.getRequestMethod());
            path.set(exchange.getRequestURI().getPath());
            accept.set(exchange.getRequestHeaders().getFirst("Accept"));
            token.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Catalog-Token"));
            sourceSystem.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            requestBody.set(read(exchange));
            send(exchange, 200, "[{\"module\":\"actiongraph-control-plane-api\"}]");
        });
        server.start();
        try {
            Map<String, String> defaultHeaders = new LinkedHashMap<>();
            defaultHeaders.put("X-Source-System", "deployment-check");
            defaultHeaders.put("X-Request-Id", "DEFAULT-CATALOG-REQUEST");
            ActionGraphComponentCatalogHttpClient client = ActionGraphComponentCatalogHttpClient
                    .builder(baseUrl(server))
                    .sharedSecret("catalog-secret")
                    .defaultHeaders(defaultHeaders)
                    .build();

            ControlPlaneHttpResponse response = client.modules(Collections.singletonMap("X-Request-Id", "REQ-CATALOG-1"));

            assertThat(method.get()).isEqualTo("GET");
            assertThat(path.get()).isEqualTo("/actiongraph/components/modules");
            assertThat(accept.get()).isEqualTo("application/json");
            assertThat(token.get()).isEqualTo("catalog-secret");
            assertThat(sourceSystem.get()).isEqualTo("deployment-check");
            assertThat(requestId.get()).isEqualTo("REQ-CATALOG-1");
            assertThat(requestBody.get()).isEmpty();
            assertThat(response.statusCode()).isEqualTo(200);
            assertThat(response.successful()).isTrue();
            assertThat(response.body()).contains("actiongraph-control-plane-api");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void encodesPathSegmentsAndPreservesErrorBody() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> modulePath = new AtomicReference<>();
        AtomicReference<String> moduleProfilesPath = new AtomicReference<>();
        AtomicReference<String> compatibilityPath = new AtomicReference<>();
        server.createContext("/actiongraph/components/modules", exchange -> {
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath.endsWith("/profiles")) {
                moduleProfilesPath.set(rawPath);
                send(exchange, 200, "[{\"name\":\"java8-legacy-client\"}]");
            } else {
                modulePath.set(rawPath);
                send(exchange, 404, "{\"error\":\"NOT_FOUND\",\"message\":\"missing\"}");
            }
        });
        server.createContext("/actiongraph/components/compatibility", exchange -> {
            compatibilityPath.set(exchange.getRequestURI().getRawPath());
            send(exchange, 200, "[]");
        });
        server.start();
        try {
            ActionGraphComponentCatalogHttpClient client = ActionGraphComponentCatalogHttpClient
                    .builder(baseUrl(server) + "/")
                    .build();

            ControlPlaneHttpResponse missing = client.module("legacy client");
            ControlPlaneHttpResponse compatible = client.modulesByCompatibility("java8 client");
            ControlPlaneHttpResponse profiles = client.profilesForModule("legacy client");

            assertThat(modulePath.get()).isEqualTo("/actiongraph/components/modules/legacy%20client");
            assertThat(missing.statusCode()).isEqualTo(404);
            assertThat(missing.successful()).isFalse();
            assertThat(missing.body()).contains("NOT_FOUND");
            assertThat(compatibilityPath.get()).isEqualTo("/actiongraph/components/compatibility/java8%20client");
            assertThat(compatible.body()).isEqualTo("[]");
            assertThat(moduleProfilesPath.get()).isEqualTo("/actiongraph/components/modules/legacy%20client/profiles");
            assertThat(profiles.body()).contains("java8-legacy-client");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void readsCatalogAndProfiles() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> catalogPath = new AtomicReference<>();
        AtomicReference<String> profilePath = new AtomicReference<>();
        server.createContext("/actiongraph/components", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/actiongraph/components/profiles/java8-legacy-client".equals(path)) {
                profilePath.set(path);
                send(exchange, 200, "{\"name\":\"java8-legacy-client\"}");
            } else if ("/actiongraph/components/profiles".equals(path)) {
                send(exchange, 200, "[{\"name\":\"java8-legacy-client\"}]");
            } else {
                catalogPath.set(path);
                send(exchange, 200, "{\"components\":[],\"profiles\":[]}");
            }
        });
        server.start();
        try {
            ActionGraphComponentCatalogHttpClient client = ActionGraphComponentCatalogHttpClient
                    .builder(baseUrl(server))
                    .build();

            ControlPlaneHttpResponse catalog = client.catalog();
            ControlPlaneHttpResponse profiles = client.profiles();
            ControlPlaneHttpResponse profile = client.profile("java8-legacy-client");

            assertThat(catalogPath.get()).isEqualTo("/actiongraph/components");
            assertThat(catalog.body()).contains("components");
            assertThat(profiles.body()).contains("java8-legacy-client");
            assertThat(profilePath.get()).isEqualTo("/actiongraph/components/profiles/java8-legacy-client");
            assertThat(profile.body()).contains("java8-legacy-client");
        } finally {
            server.stop(0);
        }
    }

    @Test
    void retriesTransientGetFailures() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicInteger attempts = new AtomicInteger();
        server.createContext("/actiongraph/components/modules", exchange -> {
            if (attempts.incrementAndGet() == 1) {
                send(exchange, 503, "{\"error\":\"UNAVAILABLE\"}");
            } else {
                send(exchange, 200, "[{\"module\":\"actiongraph-control-plane-api\"}]");
            }
        });
        server.start();
        try {
            ActionGraphComponentCatalogHttpClient client = ActionGraphComponentCatalogHttpClient
                    .builder(baseUrl(server))
                    .maxGetRetries(1)
                    .getRetryBackoffMillis(0)
                    .build();

            ControlPlaneHttpResponse response = client.modules();

            assertThat(response.successful()).isTrue();
            assertThat(response.body()).contains("actiongraph-control-plane-api");
            assertThat(attempts.get()).isEqualTo(2);
        } finally {
            server.stop(0);
        }
    }

    @Test
    void validatesRequiredFieldsBeforeSending() {
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder(" ").build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("catalogApiBaseUrl");
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder("http://localhost").build().module(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module");
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder("http://localhost").build()
                .profilesForModule(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("module");
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder("http://localhost").build().profile(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("profile");
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder("http://localhost").defaultHeader(" ", "value"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("default header name");
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder("http://localhost").build()
                .get("/modules", Collections.singletonMap(" ", "value")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("request header name");
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder("http://localhost")
                .maxGetRetries(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("maxGetRetries");
        assertThatThrownBy(() -> ActionGraphComponentCatalogHttpClient.builder("http://localhost")
                .getRetryBackoffMillis(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("getRetryBackoffMillis");
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/components";
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
