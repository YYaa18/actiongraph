package com.actiongraph.interpretation.annotation;

import com.actiongraph.api.Experimental;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.BlackboardKey;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Multi-write result for annotated goal seeders.
 */
@Experimental(
        since = "0.2.0",
        value = "Annotated seeder multi-write results are experimental until pilot domains validate the shape."
)
public final class SeedResult {
    private static final SeedResult EMPTY = new SeedResult(List.of(), Set.of());

    private final List<Value> values;
    private final Set<Condition> conditions;

    private SeedResult(List<Value> values, Set<Condition> conditions) {
        this.values = List.copyOf(values);
        this.conditions = Set.copyOf(conditions);
    }

    public static SeedResult empty() {
        return EMPTY;
    }

    public static SeedResult of(Object value) {
        return builder().put(value).build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public List<Value> values() {
        return values;
    }

    public Set<Condition> conditions() {
        return conditions;
    }

    public record Value(BlackboardKey<?> key, Object value) {
        public Value {
            key = Objects.requireNonNull(key, "key");
            value = Objects.requireNonNull(value, "value");
        }
    }

    public static final class Builder {
        private final List<Value> values = new ArrayList<>();
        private final Set<Condition> conditions = new LinkedHashSet<>();

        public Builder put(Object value) {
            Objects.requireNonNull(value, "value");
            @SuppressWarnings("unchecked")
            Class<Object> type = (Class<Object>) value.getClass();
            return put(BlackboardKey.of(type), value);
        }

        public <T> Builder put(Class<T> type, String id, T value) {
            return put(BlackboardKey.of(type, id), value);
        }

        public <T> Builder put(BlackboardKey<T> key, T value) {
            values.add(new Value(key, value));
            return this;
        }

        public Builder addCondition(String condition) {
            return addCondition(Condition.of(condition));
        }

        public Builder addCondition(Condition condition) {
            conditions.add(Objects.requireNonNull(condition, "condition"));
            return this;
        }

        public SeedResult build() {
            if (values.isEmpty() && conditions.isEmpty()) {
                return EMPTY;
            }
            return new SeedResult(values, conditions);
        }
    }
}
