package com.actiongraph.controlplane.api;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class ControlPlaneHttpClientResourceHygieneTest {
    private static final Pattern FINALLY_DISCONNECT = Pattern.compile(
            "finally\\s*\\{\\s*connection\\.disconnect\\(\\);\\s*\\}",
            Pattern.DOTALL);

    @Test
    void eachJava8HttpConnectionIsDisconnectedInFinallyBlock() throws Exception {
        assertHttpClientDisconnectsInFinally("ActionGraphRuntimeHttpClient.java");
        assertHttpClientDisconnectsInFinally("ActionGraphComponentCatalogHttpClient.java");
        assertHttpClientDisconnectsInFinally("ActionGraphHumanReviewHttpClient.java");
        assertHttpClientDisconnectsInFinally("ActionGraphConsoleHttpClient.java");
    }

    private static void assertHttpClientDisconnectsInFinally(String fileName) throws Exception {
        Path sourceFile = repositoryRoot()
                .resolve("actiongraph-control-plane-api/src/main/java/com/actiongraph/controlplane/api")
                .resolve(fileName);
        String source = Files.readString(sourceFile);

        assertThat(countOccurrences(source, ".openConnection()"))
                .as("%s should disconnect every HttpURLConnection in a finally block", fileName)
                .isEqualTo(countFinallyDisconnects(source));
    }

    private static int countFinallyDisconnects(String source) {
        Matcher matcher = FINALLY_DISCONNECT.matcher(source);
        int count = 0;
        while (matcher.find()) {
            count++;
        }
        return count;
    }

    private static int countOccurrences(String value, String needle) {
        int count = 0;
        int index = value.indexOf(needle);
        while (index >= 0) {
            count++;
            index = value.indexOf(needle, index + needle.length());
        }
        return count;
    }

    private static Path repositoryRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.exists(current.resolve("settings.gradle.kts"))
                    && Files.isDirectory(current.resolve("actiongraph-control-plane-api"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException("Could not locate repository root");
    }
}
