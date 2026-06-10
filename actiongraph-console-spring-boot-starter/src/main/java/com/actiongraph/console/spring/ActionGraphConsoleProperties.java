package com.actiongraph.console.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "actiongraph.console")
public class ActionGraphConsoleProperties {
    private boolean enabled = false;
    private String path = "/actiongraph/console";
    private String tokenHeader = "X-ActionGraph-Console-Token";
    private String sharedSecret = "";
    private int defaultLimit = 50;
    private int maxLimit = 200;

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
            throw new IllegalArgumentException("console path must not be blank");
        }
        this.path = path;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public void setTokenHeader(String tokenHeader) {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new IllegalArgumentException("console token header must not be blank");
        }
        this.tokenHeader = tokenHeader;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public void setSharedSecret(String sharedSecret) {
        if (sharedSecret != null && sharedSecret.isBlank()) {
            throw new IllegalArgumentException("console shared secret must not be blank");
        }
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public boolean hasSharedSecret() {
        return !sharedSecret.isBlank();
    }

    public int getDefaultLimit() {
        return defaultLimit;
    }

    public void setDefaultLimit(int defaultLimit) {
        if (defaultLimit <= 0) {
            throw new IllegalArgumentException("console default limit must be positive");
        }
        this.defaultLimit = defaultLimit;
    }

    public int getMaxLimit() {
        return maxLimit;
    }

    public void setMaxLimit(int maxLimit) {
        if (maxLimit <= 0) {
            throw new IllegalArgumentException("console max limit must be positive");
        }
        this.maxLimit = maxLimit;
    }
}
