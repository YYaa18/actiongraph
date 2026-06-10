package com.actiongraph.runtime;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class InMemoryBlackboardTest {
    @Test
    void defaultPutAndGetRemainCompatible() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new CustomerRecord("C001"));

        assertThat(blackboard.get(CustomerRecord.class)).contains(new CustomerRecord("C001"));
        assertThat(blackboard.get(BlackboardKey.of(CustomerRecord.class))).contains(new CustomerRecord("C001"));
        assertThat(blackboard.snapshotObjects()).containsEntry(CustomerRecord.class, new CustomerRecord("C001"));
    }

    @Test
    void storesMultipleValuesOfTheSameTypeByKey() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        BlackboardKey<CustomerRecord> primary = BlackboardKey.of(CustomerRecord.class, "primary");
        BlackboardKey<CustomerRecord> secondary = BlackboardKey.of(CustomerRecord.class, "secondary");

        blackboard.put(primary, new CustomerRecord("C001"));
        blackboard.put(secondary, new CustomerRecord("C002"));

        assertThat(blackboard.get(primary)).contains(new CustomerRecord("C001"));
        assertThat(blackboard.get(secondary)).contains(new CustomerRecord("C002"));
        assertThat(blackboard.getAll(CustomerRecord.class))
                .containsExactly(new CustomerRecord("C001"), new CustomerRecord("C002"));
        assertThat(blackboard.snapshotEntries())
                .containsEntry(primary, new CustomerRecord("C001"))
                .containsEntry(secondary, new CustomerRecord("C002"));
    }

    @Test
    void typeLookupIsAmbiguousWhenMultipleNonDefaultValuesExist() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(BlackboardKey.of(CustomerRecord.class, "primary"), new CustomerRecord("C001"));
        blackboard.put(BlackboardKey.of(CustomerRecord.class, "secondary"), new CustomerRecord("C002"));

        assertThatThrownBy(() -> blackboard.get(CustomerRecord.class))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Multiple blackboard values");
    }

    @Test
    void defaultValueWinsTypeLookupWhenDefaultAndKeyedValuesExist() {
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        blackboard.put(new CustomerRecord("DEFAULT"));
        blackboard.put(BlackboardKey.of(CustomerRecord.class, "secondary"), new CustomerRecord("C002"));

        assertThat(blackboard.get(CustomerRecord.class)).contains(new CustomerRecord("DEFAULT"));
        assertThat(blackboard.getAll(CustomerRecord.class))
                .containsExactly(new CustomerRecord("DEFAULT"), new CustomerRecord("C002"));
    }

    private record CustomerRecord(String id) {
    }
}
