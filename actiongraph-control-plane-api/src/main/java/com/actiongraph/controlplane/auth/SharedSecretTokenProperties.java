package com.actiongraph.controlplane.auth;

import org.jspecify.annotations.Nullable;

public interface SharedSecretTokenProperties {
    String getTokenHeader();

    @Nullable
    String getSharedSecret();

    default boolean hasSharedSecret() {
        String secret = getSharedSecret();
        return secret != null && !secret.trim().isEmpty();
    }

    default SharedSecretTokenProtection toTokenProtection() {
        return new SharedSecretTokenProtection(getTokenHeader(), getSharedSecret());
    }
}
