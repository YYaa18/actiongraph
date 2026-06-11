package com.actiongraph.controlplane.api;

import java.io.IOException;
import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Java 8 compatible runtime gateway contract for legacy systems that call a
 * deployed ActionGraph runtime instead of embedding runtime modules.
 */
public interface ActionGraphRuntimeGateway {
    ControlPlaneHttpResponse interpret(String input) throws IOException;

    ControlPlaneHttpResponse interpret(String input, @Nullable Map<String, String> knownParameters) throws IOException;

    ControlPlaneHttpResponse interpret(
            String input,
            @Nullable Map<String, String> knownParameters,
            @Nullable Map<String, String> requestHeaders
    ) throws IOException;

    ControlPlaneHttpResponse start(String input) throws IOException;

    ControlPlaneHttpResponse start(String input, @Nullable Map<String, String> knownParameters) throws IOException;

    ControlPlaneHttpResponse start(
            String input,
            @Nullable Map<String, String> knownParameters,
            @Nullable Map<String, String> requestHeaders
    ) throws IOException;

    ControlPlaneHttpResponse resume(String runId) throws IOException;

    ControlPlaneHttpResponse resume(String runId, @Nullable Map<String, String> requestHeaders) throws IOException;
}
