package com.actiongraph.controlplane.auth;

public record SharedSecretTokenProtection(
        String tokenHeader,
        String sharedSecret
) {
    public SharedSecretTokenProtection {
        if (tokenHeader == null || tokenHeader.isBlank()) {
            throw new IllegalArgumentException("token header must not be blank");
        }
        sharedSecret = sharedSecret == null ? "" : sharedSecret;
    }

    public boolean enabled() {
        return !sharedSecret.isBlank();
    }
}
