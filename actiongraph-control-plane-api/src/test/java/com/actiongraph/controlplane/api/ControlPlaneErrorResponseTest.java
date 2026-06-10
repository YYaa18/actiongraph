package com.actiongraph.controlplane.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ControlPlaneErrorResponseTest {
    @Test
    void createsStandardUnauthorizedResponse() {
        ControlPlaneErrorResponse response = ControlPlaneErrorResponse.unauthorized("missing token");

        assertThat(response.error()).isEqualTo("UNAUTHORIZED");
        assertThat(response.message()).isEqualTo("missing token");
    }

    @Test
    void createsStandardConflictResponses() {
        assertThat(ControlPlaneErrorResponse.conflict("already decided").error())
                .isEqualTo("CONFLICT");
        assertThat(ControlPlaneErrorResponse.notClaimable("claimed").error())
                .isEqualTo("NOT_CLAIMABLE");
    }

    @Test
    void normalizesNullMessage() {
        assertThat(ControlPlaneErrorResponse.badRequest(null).message()).isEmpty();
    }

    @Test
    void rejectsBlankErrorCode() {
        assertThatThrownBy(() -> ControlPlaneErrorResponse.of(" ", "message"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("error");
    }
}
