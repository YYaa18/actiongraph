package com.actiongraph.controlplane.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
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
    void rawHttpGatewayExampleCanBeCompiledWithoutActionGraphClasspath() throws Exception {
        compileExample(
                "8",
                repositoryRoot().resolve(
                        "docs/examples/pre-java8-http-gateway/src/main/java/com/company/legacygateway/RawHttpActionGraphGatewayUsage.java"),
                emptyClasspath().toString(),
                "com/company/legacygateway/RawHttpActionGraphGatewayUsage.class");
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

    private void compileExample(String release, Path sourceFile, String classpath, String expectedClassFile) throws Exception {
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
