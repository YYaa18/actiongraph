package com.actiongraph.controlplane.auth;

public interface SharedSecretTokenProperties {
    String getTokenHeader();

    String getSharedSecret();

    default boolean hasSharedSecret() {
        String secret = getSharedSecret();
        return secret != null && !secret.isBlank();
    }

    default SharedSecretTokenProtection toTokenProtection() {
        return new SharedSecretTokenProtection(getTokenHeader(), getSharedSecret());
    }
}
