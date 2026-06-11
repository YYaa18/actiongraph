package com.actiongraph.controlplane.api;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ControlPlaneHttpResponseTest {
    @Test
    void exposesStatusBodyAndSuccessFlag() {
        ControlPlaneHttpResponse response = new ControlPlaneHttpResponse(200, "{\"ok\":true}");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.getStatusCode()).isEqualTo(200);
        assertThat(response.body()).isEqualTo("{\"ok\":true}");
        assertThat(response.getBody()).isEqualTo("{\"ok\":true}");
        assertThat(response.successful()).isTrue();
        assertThat(response.isSuccessful()).isTrue();
    }

    @Test
    void extractsStandardControlPlaneErrorCode() {
        ControlPlaneHttpResponse response = new ControlPlaneHttpResponse(
                409,
                "{\n  \"message\" : \"already claimed\",\n  \"error\" : \"NOT_CLAIMABLE\"\n}");

        assertThat(response.error()).isEqualTo(ControlPlaneErrorResponse.NOT_CLAIMABLE);
        assertThat(response.getError()).isEqualTo(ControlPlaneErrorResponse.NOT_CLAIMABLE);
        assertThat(response.hasError(ControlPlaneErrorResponse.NOT_CLAIMABLE)).isTrue();
        assertThat(response.hasError(ControlPlaneErrorResponse.CONFLICT)).isFalse();
    }

    @Test
    void returnsEmptyErrorForNonErrorBodies() {
        assertThat(new ControlPlaneHttpResponse(200, "{\"runs\":[]}").error()).isEmpty();
        assertThat(new ControlPlaneHttpResponse(200, "runId,status\nRUN-1,COMPLETED\n").error()).isEmpty();
        assertThat(new ControlPlaneHttpResponse(204, null).error()).isEmpty();
        assertThat(new ControlPlaneHttpResponse(500, "{\"error\":42}").error()).isEmpty();
        assertThat(new ControlPlaneHttpResponse(500, "{\"message\":\"missing\"}").error()).isEmpty();
        assertThat(new ControlPlaneHttpResponse(500, "{\"error\":\"unterminated}").error()).isEmpty();
    }

    @Test
    void decodesEscapedJsonStringValues() {
        ControlPlaneHttpResponse response = new ControlPlaneHttpResponse(
                500,
                "{\"error\":\"CUSTOM_\\u0045RROR\",\"message\":\"escaped\"}");

        assertThat(response.error()).isEqualTo("CUSTOM_ERROR");
        assertThat(response.hasError("CUSTOM_ERROR")).isTrue();
        assertThat(response.hasError(null)).isFalse();
        assertThat(response.hasError(" ")).isFalse();
    }
}
