package com.actiongraph.controlplane.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Objects;
import java.util.function.Function;

public final class ControlPlaneTokenVerifier {
    public void verify(
            SharedSecretTokenProperties properties,
            Function<String, String> tokenLookup,
            String unauthorizedMessage
    ) {
        Objects.requireNonNull(properties, "properties");
        Objects.requireNonNull(tokenLookup, "tokenLookup");
        SharedSecretTokenProtection protection = properties.toTokenProtection();
        String actualToken = protection.enabled()
                ? tokenLookup.apply(protection.tokenHeader())
                : null;
        verify(protection, actualToken, unauthorizedMessage);
    }

    public void verify(
            SharedSecretTokenProtection protection,
            String actualToken,
            String unauthorizedMessage
    ) {
        if (!isAuthorized(protection, actualToken)) {
            throw new UnauthorizedControlPlaneAccessException(unauthorizedMessage);
        }
    }

    public boolean isAuthorized(SharedSecretTokenProtection protection, String actualToken) {
        Objects.requireNonNull(protection, "protection");
        if (!protection.enabled()) {
            return true;
        }
        return sameSecret(protection.sharedSecret(), actualToken);
    }

    private boolean sameSecret(String expected, String actual) {
        byte[] expectedBytes = expected.getBytes(StandardCharsets.UTF_8);
        byte[] actualBytes = actual == null
                ? new byte[0]
                : actual.getBytes(StandardCharsets.UTF_8);
        return MessageDigest.isEqual(expectedBytes, actualBytes);
    }
}
