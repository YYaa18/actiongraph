package com.actiongraph.controlplane.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class Java8ConsumerCompilationTest {
    @TempDir
    Path tempDir;

    @Test
    void publicApiCanBeCompiledByJava8ConsumerCode() throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        assertThat(compiler).as("JDK compiler must be available").isNotNull();

        Path sourceDir = tempDir.resolve("src/legacy");
        Path outputDir = tempDir.resolve("classes");
        Files.createDirectories(sourceDir);
        Files.createDirectories(outputDir);
        Path sourceFile = sourceDir.resolve("LegacyActionGraphClientUsage.java");
        Files.write(sourceFile, java8ConsumerSource().getBytes(StandardCharsets.UTF_8));

        ByteArrayOutputStream stderr = new ByteArrayOutputStream();
        int exitCode = compiler.run(null, null, stderr,
                "--release", "8",
                "-classpath", System.getProperty("java.class.path"),
                "-d", outputDir.toString(),
                sourceFile.toString());

        assertThat(exitCode)
                .as(new String(stderr.toByteArray(), StandardCharsets.UTF_8))
                .isZero();
        assertThat(outputDir.resolve("legacy/LegacyActionGraphClientUsage.class")).exists();
    }

    private String java8ConsumerSource() {
        return String.join("\n",
                "package legacy;",
                "",
                "import com.actiongraph.controlplane.api.ActionGraphRuntimeHttpClient;",
                "import com.actiongraph.controlplane.api.ControlPlaneErrorResponse;",
                "import com.actiongraph.controlplane.api.ControlPlaneHttpResponse;",
                "import com.actiongraph.controlplane.auth.ControlPlaneTokenVerifier;",
                "import com.actiongraph.controlplane.auth.SharedSecretTokenProperties;",
                "import com.actiongraph.controlplane.auth.SharedSecretTokenProtection;",
                "import com.actiongraph.controlplane.auth.UnauthorizedControlPlaneAccessException;",
                "import java.util.HashMap;",
                "import java.util.Map;",
                "import java.util.function.Function;",
                "",
                "public final class LegacyActionGraphClientUsage {",
                "    private LegacyActionGraphClientUsage() {",
                "    }",
                "",
                "    public static void useApi() throws Exception {",
                "        Map<String, String> known = new HashMap<String, String>();",
                "        known.put(\"customerId\", \"C001\");",
                "",
                "        ActionGraphRuntimeHttpClient client = ActionGraphRuntimeHttpClient",
                "                .builder(\"https://agent.example.com/actiongraph/runtime\")",
                "                .tokenHeader(ActionGraphRuntimeHttpClient.DEFAULT_RUNTIME_TOKEN_HEADER)",
                "                .sharedSecret(\"secret\")",
                "                .connectTimeoutMillis(1000)",
                "                .readTimeoutMillis(2000)",
                "                .build();",
                "        if (client == null) {",
                "            throw new IllegalStateException(\"client\");",
                "        }",
                "",
                "        ControlPlaneHttpResponse response = new ControlPlaneHttpResponse(200, \"{}\");",
                "        if (!response.successful() || !response.isSuccessful()",
                "                || response.statusCode() != response.getStatusCode()",
                "                || !response.body().equals(response.getBody())) {",
                "            throw new IllegalStateException(\"response\");",
                "        }",
                "",
                "        ControlPlaneErrorResponse error = ControlPlaneErrorResponse.notClaimable(\"claimed\");",
                "        if (!ControlPlaneErrorResponse.NOT_CLAIMABLE.equals(error.error())",
                "                || !error.error().equals(error.getError())",
                "                || !error.message().equals(error.getMessage())) {",
                "            throw new IllegalStateException(\"error\");",
                "        }",
                "",
                "        SharedSecretTokenProtection protection =",
                "                new SharedSecretTokenProtection(\"X-ActionGraph-Runtime-Token\", \"secret\");",
                "        ControlPlaneTokenVerifier verifier = new ControlPlaneTokenVerifier();",
                "        if (!protection.enabled() || !verifier.isAuthorized(protection, \"secret\")) {",
                "            throw new IllegalStateException(\"token\");",
                "        }",
                "",
                "        SharedSecretTokenProperties properties = new SharedSecretTokenProperties() {",
                "            public String getTokenHeader() {",
                "                return \"X-ActionGraph-Runtime-Token\";",
                "            }",
                "",
                "            public String getSharedSecret() {",
                "                return \"secret\";",
                "            }",
                "        };",
                "        verifier.verify(properties, new Function<String, String>() {",
                "            public String apply(String header) {",
                "                return \"secret\";",
                "            }",
                "        }, \"unauthorized\");",
                "",
                "        try {",
                "            verifier.verify(protection, \"wrong\", \"unauthorized\");",
                "            throw new IllegalStateException(\"expected unauthorized\");",
                "        } catch (UnauthorizedControlPlaneAccessException expected) {",
                "            if (!\"unauthorized\".equals(expected.getMessage())) {",
                "                throw expected;",
                "            }",
                "        }",
                "    }",
                "}",
                "");
    }
}
