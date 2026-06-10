package com.actiongraph.catalog.spring;

import com.actiongraph.controlplane.auth.SharedSecretTokenProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "actiongraph.component-catalog")
public class ActionGraphComponentCatalogProperties implements SharedSecretTokenProperties {
    private boolean enabled = false;
    private String path = "/actiongraph/components";
    private String tokenHeader = "X-ActionGraph-Catalog-Token";
    private String sharedSecret = "";

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
            throw new IllegalArgumentException("component catalog path must not be blank");
        }
        this.path = path;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new IllegalArgumentException("component catalog token header must not be blank");
        }
        this.tokenHeader = tokenHeader;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        if (sharedSecret != null && sharedSecret.isBlank()) {
            throw new IllegalArgumentException("component catalog shared secret must not be blank");
        }
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public boolean hasSharedSecret() {
        return !sharedSecret.isBlank();
    }
}
