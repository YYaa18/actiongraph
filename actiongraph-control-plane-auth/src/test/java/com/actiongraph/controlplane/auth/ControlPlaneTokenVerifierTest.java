package com.actiongraph.controlplane.auth;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlPlaneTokenVerifierTest {
    private final ControlPlaneTokenVerifier verifier = new ControlPlaneTokenVerifier();

    @Test
    void authorizesWhenSharedSecretIsDisabled() {
        SharedSecretTokenProtection protection = new SharedSecretTokenProtection("X-Test-Token", "");

        assertThat(verifier.isAuthorized(protection, null)).isTrue();
        verifier.verify(protection, null, "unauthorized");
    }

    @Test
    void rejectsMissingOrWrongTokenWhenSharedSecretIsConfigured() {
        SharedSecretTokenProtection protection = new SharedSecretTokenProtection("X-Test-Token", "secret");

        assertThat(verifier.isAuthorized(protection, null)).isFalse();
        assertThat(verifier.isAuthorized(protection, "wrong")).isFalse();
        assertThatThrownBy(() -> verifier.verify(protection, "wrong", "custom unauthorized"))
                .isInstanceOf(UnauthorizedControlPlaneAccessException.class)
                .hasMessage("custom unauthorized");
    }

    @Test
    void acceptsExactTokenValue() {
        SharedSecretTokenProtection protection = new SharedSecretTokenProtection("X-Test-Token", "secret");

        assertThat(verifier.isAuthorized(protection, "secret")).isTrue();
        verifier.verify(protection, "secret", "unauthorized");
    }

    @Test
    void canReadTokenThroughPropertiesAndLookupFunction() {
        TestProperties properties = new TestProperties("X-Test-Token", "secret");
        Map<String, String> headers = Map.of("X-Test-Token", "secret");

        verifier.verify(properties, headers::get, "unauthorized");
    }

    @Test
    void validatesTokenHeaderName() {
        assertThatThrownBy(() -> new SharedSecretTokenProtection("", "secret"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("token header");
    }

    private record TestProperties(
            String tokenHeader,
            String sharedSecret
    ) implements SharedSecretTokenProperties {
        @Override
        public String getTokenHeader() {
            return tokenHeader;
        }

        @Override
        public String getSharedSecret() {
            return sharedSecret;
        }
    }
}
