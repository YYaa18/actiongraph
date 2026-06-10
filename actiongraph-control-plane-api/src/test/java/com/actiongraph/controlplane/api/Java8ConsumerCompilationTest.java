package com.actiongraph.controlplane.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Java8ConsumerCompilationTest {
    @TempDir
    Path tempDir;

    @Test
    void publicApiCanBeCompiledByJava8ConsumerCode() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler must be available").isNotNull();

        Path outputDir = tempDir.resolve("classes");
        java.nio.file.Files.createDirectories(outputDir);
        Path sourceFile = repositoryRoot().resolve(
                "docs/examples/java8-legacy-client/src/main/java/com/company/legacy/LegacyActionGraphClientUsage.java");

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = compiler.run(null, null, stderr,
                "--release", "8",
                "-encoding", "UTF-8",
                "-classpath", System.getProperty("java.class.path"),
                "-d", outputDir.toString(),
                sourceFile.toString());

        assertThat(exitCode)
                .as(new String(stderr.toByteArray(), StandardCharsets.UTF_8))
                .isZero();
        assertThat(outputDir.resolve("com/company/legacy/LegacyActionGraphClientUsage.class")).exists();
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
}
