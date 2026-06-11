package com.actiongraph.controlplane.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.sun.net.httpserver.HttpServer;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Method;
import java.net.InetSocketAddress;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class Java8ConsumerCompilationTest {
    @TempDir
    Path tempDir;

    @Test
    void publicApiCanBeCompiledByJava8ConsumerCode() throws Exception {
        compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/java8-legacy-client/src/main/java/com/company/legacy/LegacyActionGraphClientUsage.java"),
                mainClassesClasspath(),
                "com/company/legacy/LegacyActionGraphClientUsage.class");
    }

    @Test
    void catalogHttpClientExampleCanBeCompiledByJava8ConsumerCode() throws Exception {
        compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/java8-catalog-http-client/src/main/java/com/company/deployment/ActionGraphCatalogHttpClientUsage.java"),
                mainClassesClasspath(),
                "com/company/deployment/ActionGraphCatalogHttpClientUsage.class");
    }

    @Test
    void controlPlaneHttpClientExampleCanBeCompiledByJava8ConsumerCode() throws Exception {
        compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/java8-control-plane-client/src/main/java/com/company/controlplane/ActionGraphControlPlaneClientUsage.java"),
                mainClassesClasspath(),
                "com/company/controlplane/ActionGraphControlPlaneClientUsage.class");
    }

    @Test
    void humanReviewHttpClientExampleCanBeCompiledByJava8ConsumerCode() throws Exception {
        compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/java8-human-review-client/src/main/java/com/company/approval/ActionGraphHumanReviewClientUsage.java"),
                mainClassesClasspath(),
                "com/company/approval/ActionGraphHumanReviewClientUsage.class");
    }

    @Test
    void consoleHttpClientExampleCanBeCompiledByJava8ConsumerCode() throws Exception {
        compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/java8-console-client/src/main/java/com/company/audit/ActionGraphConsoleClientUsage.java"),
                mainClassesClasspath(),
                "com/company/audit/ActionGraphConsoleClientUsage.class");
    }

    @Test
    void rawHttpGatewayExampleCanBeCompiledWithoutActionGraphClasspath() throws Exception {
        compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java"),
                emptyClasspath().toString(),
                "com/company/legacygateway/RawHttpActionGraphGatewayUsage.class");
    }

    @Test
    void rawHttpGatewayExampleSendsExtraAuditHeaders() throws Exception {
        Path outputDir = compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java"),
                emptyClasspath().toString(),
                "com/company/legacygateway/RawHttpActionGraphGatewayUsage.class");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> sourceSystem = new AtomicReference<>();
        AtomicReference<String> requestId = new AtomicReference<>();
        AtomicReference<String> token = new AtomicReference<>();
        server.createContext("/actiongraph/runtime/runs", exchange -> {
            sourceSystem.set(exchange.getRequestHeaders().getFirst("X-Source-System"));
            requestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            token.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Runtime-Token"));
            exchange.getRequestBody().close();
            send(exchange, 200, "{\"disposition\":\"RUN_STARTED\"}");
        });
        server.start();
        try {
            URLClassLoader loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()}, null);
            try {
                Class<?> gateway = Class.forName(
                        "com.company.legacygateway.RawHttpActionGraphGatewayUsage", true, loader);
                Method start = gateway.getMethod("start", String.class, String.class, String.class,
                        Map.class, Map.class);
                Map<String, String> known = new HashMap<>();
                known.put("customerId", "C001");
                Map<String, String> extraHeaders = new HashMap<>();
                extraHeaders.put("X-Source-System", "legacy-core");
                extraHeaders.put("X-Request-Id", "REQ-RAW-1");

                Object response = start.invoke(null, baseUrl(server), "secret", "Prepare renewal quote for C001",
                        known, extraHeaders);

                assertThat(response.getClass().getMethod("statusCode").invoke(response)).isEqualTo(200);
                assertThat(sourceSystem.get()).isEqualTo("legacy-core");
                assertThat(requestId.get()).isEqualTo("REQ-RAW-1");
                assertThat(token.get()).isEqualTo("secret");
            } finally {
                loader.close();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rawHttpGatewayExampleCallsComponentCatalogEndpoints() throws Exception {
        Path outputDir = compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java"),
                emptyClasspath().toString(),
                "com/company/legacygateway/RawHttpActionGraphGatewayUsage.class");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> catalogMethod = new AtomicReference<>();
        AtomicReference<String> catalogToken = new AtomicReference<>();
        AtomicReference<String> catalogRequestId = new AtomicReference<>();
        AtomicReference<String> catalogAccept = new AtomicReference<>();
        AtomicReference<String> catalogPath = new AtomicReference<>();
        AtomicReference<String> modulesPath = new AtomicReference<>();
        AtomicReference<String> modulePath = new AtomicReference<>();
        AtomicReference<String> moduleProfilesPath = new AtomicReference<>();
        AtomicReference<String> compatibilityPath = new AtomicReference<>();
        AtomicReference<String> profilesPath = new AtomicReference<>();
        AtomicReference<String> profilePath = new AtomicReference<>();
        server.createContext("/actiongraph/components", exchange -> {
            catalogMethod.set(exchange.getRequestMethod());
            catalogToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Catalog-Token"));
            catalogRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            catalogAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
            catalogPath.set(exchange.getRequestURI().getRawPath());
            exchange.getRequestBody().close();
            send(exchange, 200, "{\"components\":[]}");
        });
        server.createContext("/actiongraph/components/modules", exchange -> {
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath.equals("/actiongraph/components/modules")) {
                modulesPath.set(rawPath);
                send(exchange, 200, "[]");
            } else if (rawPath.endsWith("/profiles")) {
                moduleProfilesPath.set(rawPath);
                send(exchange, 200, "[{\"name\":\"java8-legacy-client\"}]");
            } else {
                modulePath.set(rawPath);
                send(exchange, 200, "{\"module\":\"actiongraph module/alpha\"}");
            }
        });
        server.createContext("/actiongraph/components/compatibility", exchange -> {
            compatibilityPath.set(exchange.getRequestURI().getRawPath());
            send(exchange, 200, "[]");
        });
        server.createContext("/actiongraph/components/profiles", exchange -> {
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath.equals("/actiongraph/components/profiles")) {
                profilesPath.set(rawPath);
                send(exchange, 200, "[]");
            } else {
                profilePath.set(rawPath);
                send(exchange, 200, "{\"name\":\"pilot profile\"}");
            }
        });
        server.start();
        try {
            URLClassLoader loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()}, null);
            try {
                Class<?> gateway = Class.forName(
                        "com.company.legacygateway.RawHttpActionGraphGatewayUsage", true, loader);
                Map<String, String> extraHeaders = new HashMap<>();
                extraHeaders.put("X-Request-Id", "REQ-RAW-CATALOG-1");

                Method catalog = gateway.getMethod("componentCatalog", String.class, String.class, Map.class);
                Method modules = gateway.getMethod("componentModules", String.class, String.class, Map.class);
                Method compatibility = gateway.getMethod("componentModulesByCompatibility",
                        String.class, String.class, String.class, Map.class);
                Method module = gateway.getMethod("componentModule", String.class, String.class,
                        String.class, Map.class);
                Method moduleProfiles = gateway.getMethod("componentProfilesForModule", String.class, String.class,
                        String.class, Map.class);
                Method profiles = gateway.getMethod("componentProfiles", String.class, String.class, Map.class);
                Method profile = gateway.getMethod("componentProfile", String.class, String.class,
                        String.class, Map.class);

                Object catalogResponse = catalog.invoke(null, catalogBaseUrl(server), "catalog-secret", extraHeaders);
                Object modulesResponse = modules.invoke(null, catalogBaseUrl(server), "catalog-secret", extraHeaders);
                Object compatibilityResponse = compatibility.invoke(null, catalogBaseUrl(server), "catalog-secret",
                        "java 8+", extraHeaders);
                Object moduleResponse = module.invoke(null, catalogBaseUrl(server), "catalog-secret",
                        "actiongraph module/alpha", extraHeaders);
                Object moduleProfilesResponse = moduleProfiles.invoke(null, catalogBaseUrl(server), "catalog-secret",
                        "actiongraph module/alpha", extraHeaders);
                Object profilesResponse = profiles.invoke(null, catalogBaseUrl(server), "catalog-secret",
                        extraHeaders);
                Object profileResponse = profile.invoke(null, catalogBaseUrl(server), "catalog-secret",
                        "pilot profile", extraHeaders);

                assertThat(catalogResponse.getClass().getMethod("statusCode").invoke(catalogResponse)).isEqualTo(200);
                assertThat(modulesResponse.getClass().getMethod("statusCode").invoke(modulesResponse)).isEqualTo(200);
                assertThat(compatibilityResponse.getClass().getMethod("statusCode").invoke(compatibilityResponse))
                        .isEqualTo(200);
                assertThat(moduleResponse.getClass().getMethod("statusCode").invoke(moduleResponse)).isEqualTo(200);
                assertThat(moduleProfilesResponse.getClass().getMethod("statusCode").invoke(moduleProfilesResponse))
                        .isEqualTo(200);
                assertThat(profilesResponse.getClass().getMethod("statusCode").invoke(profilesResponse)).isEqualTo(200);
                assertThat(profileResponse.getClass().getMethod("statusCode").invoke(profileResponse)).isEqualTo(200);
                assertThat(catalogMethod.get()).isEqualTo("GET");
                assertThat(catalogToken.get()).isEqualTo("catalog-secret");
                assertThat(catalogRequestId.get()).isEqualTo("REQ-RAW-CATALOG-1");
                assertThat(catalogAccept.get()).isEqualTo("application/json");
                assertThat(catalogPath.get()).isEqualTo("/actiongraph/components");
                assertThat(modulesPath.get()).isEqualTo("/actiongraph/components/modules");
                assertThat(compatibilityPath.get())
                        .isEqualTo("/actiongraph/components/compatibility/java%208%2B");
                assertThat(modulePath.get())
                        .isEqualTo("/actiongraph/components/modules/actiongraph%20module%2Falpha");
                assertThat(moduleProfilesPath.get())
                        .isEqualTo("/actiongraph/components/modules/actiongraph%20module%2Falpha/profiles");
                assertThat(profilesPath.get()).isEqualTo("/actiongraph/components/profiles");
                assertThat(profilePath.get()).isEqualTo("/actiongraph/components/profiles/pilot%20profile");
            } finally {
                loader.close();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rawHttpGatewayExampleCallsReviewAndConsoleEndpoints() throws Exception {
        Path outputDir = compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java"),
                emptyClasspath().toString(),
                "com/company/legacygateway/RawHttpActionGraphGatewayUsage.class");
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        AtomicReference<String> reviewToken = new AtomicReference<>();
        AtomicReference<String> reviewRequestId = new AtomicReference<>();
        AtomicReference<String> pendingMethod = new AtomicReference<>();
        AtomicReference<String> decisionPath = new AtomicReference<>();
        AtomicReference<String> decisionBody = new AtomicReference<>();
        AtomicReference<String> callbackPath = new AtomicReference<>();
        AtomicReference<String> callbackBody = new AtomicReference<>();
        AtomicReference<String> consoleQuery = new AtomicReference<>();
        AtomicReference<String> consoleToken = new AtomicReference<>();
        AtomicReference<String> consoleJsonlPath = new AtomicReference<>();
        AtomicReference<String> consoleJsonlAccept = new AtomicReference<>();
        server.createContext("/actiongraph/human-review/tasks/pending", exchange -> {
            pendingMethod.set(exchange.getRequestMethod());
            reviewToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Review-Token"));
            reviewRequestId.set(exchange.getRequestHeaders().getFirst("X-Request-Id"));
            exchange.getRequestBody().close();
            send(exchange, 200, "[{\"runId\":\"RUN-1\"}]");
        });
        server.createContext("/actiongraph/human-review/tasks/runs", exchange -> {
            decisionPath.set(exchange.getRequestURI().getRawPath());
            decisionBody.set(read(exchange));
            send(exchange, 200, "{\"decision\":\"APPROVED\"}");
        });
        server.createContext("/actiongraph/human-review/callbacks", exchange -> {
            callbackPath.set(exchange.getRequestURI().getPath());
            callbackBody.set(read(exchange));
            send(exchange, 200, "{\"stageDecisionCount\":1}");
        });
        server.createContext("/actiongraph/console/runs", exchange -> {
            String rawPath = exchange.getRequestURI().getRawPath();
            if (rawPath.endsWith("/trace/export.jsonl")) {
                consoleJsonlPath.set(rawPath);
                consoleJsonlAccept.set(exchange.getRequestHeaders().getFirst("Accept"));
                send(exchange, 200, "{\"seq\":1}\n");
            } else {
                consoleQuery.set(exchange.getRequestURI().getRawQuery());
                consoleToken.set(exchange.getRequestHeaders().getFirst("X-ActionGraph-Console-Token"));
                send(exchange, 200, "{\"runs\":[]}");
            }
        });
        server.start();
        try {
            URLClassLoader loader = new URLClassLoader(new URL[]{outputDir.toUri().toURL()}, null);
            try {
                Class<?> gateway = Class.forName(
                        "com.company.legacygateway.RawHttpActionGraphGatewayUsage", true, loader);
                Map<String, String> extraHeaders = new HashMap<>();
                extraHeaders.put("X-Request-Id", "REQ-RAW-REVIEW-1");

                Method pending = gateway.getMethod("pendingReviewTasks", String.class, String.class, Map.class);
                Method decide = gateway.getMethod("decideReviewTask", String.class, String.class, String.class,
                        String.class, int.class, String.class, String.class, String.class, Map.class);
                Method callback = gateway.getMethod("reviewCallback", String.class, String.class, String.class,
                        String.class, int.class, String.class, String.class, String.class, Map.class);
                Method consoleRuns = gateway.getMethod("consoleRuns", String.class, String.class, Integer.class,
                        Integer.class, String.class, Boolean.class, Map.class);
                Method consoleJsonl = gateway.getMethod("consoleTraceJsonl", String.class, String.class,
                        String.class, Map.class);

                Object pendingResponse = pending.invoke(null, reviewTasksBaseUrl(server), "review-secret", extraHeaders);
                Object decisionResponse = decide.invoke(null, reviewTasksBaseUrl(server), "review-secret",
                        "RUN 1", "claim.approval/request", 0, "APPROVED", "checker", "同意 \"支付\"\n复核",
                        extraHeaders);
                callback.invoke(null, callbackBaseUrl(server), "review-secret",
                        "RUN-1", "claim.approval.request", 0, "APPROVED", "checker", "approved", extraHeaders);
                Object runsResponse = consoleRuns.invoke(null, consoleBaseUrl(server), "console-secret",
                        Integer.valueOf(25), Integer.valueOf(10), "COMPLETED", Boolean.TRUE, extraHeaders);
                Object jsonlResponse = consoleJsonl.invoke(null, consoleBaseUrl(server), "console-secret",
                        "RUN 1", extraHeaders);

                assertThat(pendingResponse.getClass().getMethod("statusCode").invoke(pendingResponse)).isEqualTo(200);
                assertThat(decisionResponse.getClass().getMethod("statusCode").invoke(decisionResponse)).isEqualTo(200);
                assertThat(runsResponse.getClass().getMethod("statusCode").invoke(runsResponse)).isEqualTo(200);
                assertThat(jsonlResponse.getClass().getMethod("statusCode").invoke(jsonlResponse)).isEqualTo(200);
                assertThat(pendingMethod.get()).isEqualTo("GET");
                assertThat(reviewToken.get()).isEqualTo("review-secret");
                assertThat(reviewRequestId.get()).isEqualTo("REQ-RAW-REVIEW-1");
                assertThat(decisionPath.get())
                        .isEqualTo("/actiongraph/human-review/tasks/runs/RUN%201/actions/claim.approval%2Frequest/decision");
                assertThat(decisionBody.get())
                        .contains("\"expectedStageIndex\":0")
                        .contains("\"decision\":\"APPROVED\"")
                        .contains("\\\"支付\\\"")
                        .contains("\\n");
                assertThat(callbackPath.get()).isEqualTo("/actiongraph/human-review/callbacks");
                assertThat(callbackBody.get()).contains("\"runId\":\"RUN-1\"");
                assertThat(consoleQuery.get()).isEqualTo("limit=25&offset=10&status=COMPLETED&auditComplete=true");
                assertThat(consoleToken.get()).isEqualTo("console-secret");
                assertThat(consoleJsonlPath.get()).isEqualTo("/actiongraph/console/runs/RUN%201/trace/export.jsonl");
                assertThat(consoleJsonlAccept.get()).isEqualTo("application/x-ndjson");
            } finally {
                loader.close();
            }
        } finally {
            server.stop(0);
        }
    }

    @Test
    void rawHttpGatewayExampleDoesNotUseActionGraphJava7OrJava8Conveniences() throws Exception {
        Path sourceFile = repositoryRoot().resolve(
                "docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java");
        String source = java.nio.file.Files.readString(sourceFile);

        assertThat(source).doesNotContain("import com.actiongraph.");
        assertThat(source).doesNotContain("try (");
        assertThat(source).doesNotContain("->");
        assertThat(source).doesNotContain("::");
        assertThat(source).doesNotContain("java.util.function");
        assertThat(source).doesNotContain("java.util.stream");
        assertThat(source).doesNotContain("java.util.Optional");
        assertThat(source).doesNotContain("java.util.Objects");
        assertThat(source).doesNotContain("java.time.");
        assertThat(source).doesNotContain("java.nio.");
        assertThat(source).doesNotContain("StandardCharsets");
        assertThat(source).doesNotContain(".isBlank(");
        assertThat(source).doesNotContain("Map.of(");
        assertThat(source).doesNotContain("List.of(");
        assertThat(source).doesNotContain("Set.of(");
        assertThat(source).doesNotContain("var ");
        assertThat(source).doesNotContain("record ");
        assertNoPattern(source, "new\\s+[^;\\n]+<>\\s*\\(");
        assertNoPattern(source, "catch\\s*\\([^)]*\\|[^)]*\\)");
        assertNoPattern(source, "\\b0[bB][01_]+");
        assertNoPattern(source, "\\d_\\d");
    }

    private Path compileExample(String release, Path sourceFile, String classpath, String expectedClassFile) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler must be available").isNotNull();

        Path outputDir = tempDir.resolve("classes-" + release);
        java.nio.file.Files.createDirectories(outputDir);

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = compiler.run(null, null, stderr,
                "--release", release,
                "-encoding", "UTF-8",
                "-classpath", classpath,
                "-d", outputDir.toString(),
                sourceFile.toString());

        assertThat(exitCode)
                .as(new String(stderr.toByteArray(), StandardCharsets.UTF_8))
                .isZero();
        assertThat(outputDir.resolve(expectedClassFile)).exists();
        return outputDir;
    }

    private static String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/runtime";
    }

    private static String reviewTasksBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/human-review/tasks";
    }

    private static String callbackBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/human-review/callbacks";
    }

    private static String catalogBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/components";
    }

    private static String consoleBaseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/actiongraph/console";
    }

    private static String read(com.sun.net.httpserver.HttpExchange exchange) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] bytes = new byte[1024];
        int read;
        while ((read = exchange.getRequestBody().read(bytes)) != -1) {
            buffer.write(bytes, 0, read);
        }
        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
    }

    private static void send(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
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

    private String mainClassesClasspath() throws Exception {
        return Path.of(ControlPlaneHttpResponse.class.getProtectionDomain()
                        .getCodeSource()
                        .getLocation()
                        .toURI())
                .toString();
    }

    private Path emptyClasspath() throws Exception {
        Path empty = tempDir.resolve("empty-classpath");
        java.nio.file.Files.createDirectories(empty);
        return empty;
    }

    private Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (java.nio.file.Files.exists(current.resolve("settings.gradle.kts"))
                    && java.nio.file.Files.isDirectory(current.resolve("actiongraph-control-plane-api"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }

    private static void assertNoPattern(String source, String regex) {
        assertThat(Pattern.compile(regex).matcher(source).find())
                .as("source should not match %s", regex)
                .isFalse();
    }
}
