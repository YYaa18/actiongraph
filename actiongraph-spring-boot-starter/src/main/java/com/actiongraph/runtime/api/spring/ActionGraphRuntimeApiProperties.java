package com.actiongraph.runtime.api.spring;

import com.actiongraph.controlplane.auth.SharedSecretTokenProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@ConfigurationProperties(prefix = "actiongraph.runtime.api")
public class ActionGraphRuntimeApiProperties implements SharedSecretTokenProperties {
    private boolean enabled = false;
    private String path = "/actiongraph/runtime";
    private String tokenHeader = "X-ActionGraph-Runtime-Token";
    private String sharedSecret = "";
    private List<String> traceHeaders = new ArrayList<>(List.of(
            "X-Request-Id",
            "X-Correlation-Id",
            "X-Source-System"
    ));

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        if (path == null || path.isBlank()) {
            throw new IllegalArgumentException("runtime API path must not be blank");
        }
        this.path = path;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new IllegalArgumentException("runtime API token header must not be blank");
        }
        this.tokenHeader = tokenHeader;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        if (sharedSecret != null && sharedSecret.isBlank()) {
            throw new IllegalArgumentException("runtime API shared secret must not be blank");
        }
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public boolean hasSharedSecret() {
        return !sharedSecret.isBlank();
    }

    public List<String> getTraceHeaders() {
        return List.copyOf(traceHeaders);
    }

    public void setTraceHeaders(List<String> traceHeaders) {
        if (traceHeaders == null) {
            this.traceHeaders = new ArrayList<>();
            return;
        }
        List<String> safeHeaders = new ArrayList<>();
        for (String traceHeader : traceHeaders) {
            if (traceHeader == null || traceHeader.isBlank()) {
                throw new IllegalArgumentException("runtime API trace header names must not be blank");
            }
            safeHeaders.add(traceHeader);
        }
        this.traceHeaders = safeHeaders;
    }
}
