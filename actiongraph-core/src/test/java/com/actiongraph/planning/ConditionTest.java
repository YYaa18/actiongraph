package com.actiongraph.planning;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ConditionTest {
    @Test
    void supportsLegacyUnnamespacedKeys() {
        Condition condition = Condition.of("DONE");

        assertThat(condition.key()).isEqualTo("DONE");
        assertThat(condition.namespace()).isEmpty();
        assertThat(condition.name()).isEqualTo("DONE");
    }

    @Test
    void supportsNamespacedKeys() {
        Condition condition = Condition.of("order-cancellation", "ORDER_ID_PRESENT");

        assertThat(condition.key()).isEqualTo("order-cancellation:ORDER_ID_PRESENT");
        assertThat(condition.namespace()).isEqualTo("order-cancellation");
        assertThat(condition.name()).isEqualTo("ORDER_ID_PRESENT");
        assertThat(condition).isEqualTo(Condition.of("order-cancellation:ORDER_ID_PRESENT"));
    }

    @Test
    void rejectsInvalidNamespaceFactoryInput() {
        assertThatThrownBy(() -> Condition.of("bad:namespace", "DONE"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> Condition.of("test", "BAD:KEY"))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
