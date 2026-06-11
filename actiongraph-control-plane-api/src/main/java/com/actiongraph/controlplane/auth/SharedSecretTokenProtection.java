package com.actiongraph.controlplane.auth;

import org.jspecify.annotations.Nullable;

public final class SharedSecretTokenProtection {
    private final String tokenHeader;
    private final String sharedSecret;

    public SharedSecretTokenProtection(String tokenHeader, @Nullable String sharedSecret) {
        if (isBlank(tokenHeader)) {
            throw new IllegalArgumentException("token header must not be blank");
        }
        this.tokenHeader = tokenHeader;
        this.sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public String tokenHeader() {
        return tokenHeader;
    }

    public String sharedSecret() {
        return sharedSecret;
    }

    public String getTokenHeader() {
        return tokenHeader;
    }

    public String getSharedSecret() {
        return sharedSecret;
    }

    public boolean enabled() {
        return !isBlank(sharedSecret);
    }

    private static boolean isBlank(@Nullable String value) {
        return value == null || value.trim().isEmpty();
    }
}
