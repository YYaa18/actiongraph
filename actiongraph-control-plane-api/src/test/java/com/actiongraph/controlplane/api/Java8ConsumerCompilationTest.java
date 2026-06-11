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
