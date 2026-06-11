package com.actiongraph.durability;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class IdempotencyKeyTest {
    @Test
    void formatsStandardHeaderValue() {
        IdempotencyKey key = new IdempotencyKey("RUN-1", "billing.charge", 2);

        assertThat(IdempotencyKey.HEADER_NAME).isEqualTo("X-ActionGraph-Idempotency-Key");
        assertThat(key.asHeaderValue()).isEqualTo("RUN-1/billing.charge/2");
    }

    @Test
    void validatesInputs() {
        assertThatThrownBy(() -> new IdempotencyKey("", "action", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("runId");
        assertThatThrownBy(() -> new IdempotencyKey("RUN-1", " ", 1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("actionId");
        assertThatThrownBy(() -> new IdempotencyKey("RUN-1", "action", 0))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("attempt");
    }
}
