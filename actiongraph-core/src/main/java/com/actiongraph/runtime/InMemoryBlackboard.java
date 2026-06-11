package com.actiongraph.runtime;

import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.planning.Condition;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public final class InMemoryBlackboard implements Blackboard {
    private final Map<BlackboardKey<?>, Object> objects = new LinkedHashMap<>();
    private final Set<Condition> conditions = new LinkedHashSet<>();

    @Override
    public synchronized <T> Optional<T> get(Class<T> type) {
        Objects.requireNonNull(type, "type");
        Object defaultValue = objects.get(BlackboardKey.of(type));
        if (defaultValue != null) {
            return Optional.of(type.cast(defaultValue));
        }

        List<Object> matches = objects.entrySet().stream()
                .filter(entry -> entry.getKey().type().equals(type))
                .map(Map.Entry::getValue)
                .toList();
        if (matches.isEmpty()) {
            return Optional.empty();
        }
        if (matches.size() == 1) {
            return Optional.of(type.cast(matches.getFirst()));
        }
        throw new ActionGraphInputException(
                "Multiple blackboard values exist for " + type.getName() + "; use a BlackboardKey");
    }

    @Override
    public synchronized <T> Optional<T> get(BlackboardKey<T> key) {
        Objects.requireNonNull(key, "key");
        return Optional.ofNullable(key.type().cast(objects.get(key)));
    }

    @Override
    public synchronized <T> void put(T value) {
        Objects.requireNonNull(value, "value");
        @SuppressWarnings("unchecked")
        Class<T> type = (Class<T>) value.getClass();
        put(BlackboardKey.of(type), value);
    }

    @Override
    public synchronized <T> void put(BlackboardKey<T> key, T value) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(value, "value");
        if (!key.type().isInstance(value)) {
            throw new IllegalArgumentException(
                    "Blackboard value " + value.getClass().getName()
                            + " is not an instance of " + key.type().getName());
        }
        objects.put(key, value);
    }

    @Override
    public synchronized boolean has(Class<?> type) {
        Objects.requireNonNull(type, "type");
        return objects.keySet().stream().anyMatch(key -> key.type().equals(type));
    }

    @Override
    public synchronized boolean has(BlackboardKey<?> key) {
        Objects.requireNonNull(key, "key");
        return objects.containsKey(key);
    }

    @Override
    public synchronized <T> List<T> getAll(Class<T> type) {
        Objects.requireNonNull(type, "type");
        return objects.entrySet().stream()
                .filter(entry -> entry.getKey().type().equals(type))
                .sorted(Comparator.comparing(entry -> entry.getKey().id()))
                .map(entry -> type.cast(entry.getValue()))
                .toList();
    }

    @Override
    public synchronized Set<Condition> conditions() {
        return Set.copyOf(conditions);
    }

    @Override
    public synchronized void addCondition(Condition condition) {
        conditions.add(Objects.requireNonNull(condition, "condition"));
    }

    @Override
    public synchronized Map<Class<?>, Object> snapshotObjects() {
        Map<Class<?>, Object> snapshot = new LinkedHashMap<>();
        objects.entrySet().stream()
                .filter(entry -> entry.getKey().isDefault())
                .forEach(entry -> snapshot.put(entry.getKey().type(), entry.getValue()));
        objects.entrySet().stream()
                .filter(entry -> !snapshot.containsKey(entry.getKey().type()))
                .filter(entry -> getAll(entry.getKey().type()).size() == 1)
                .forEach(entry -> snapshot.put(entry.getKey().type(), entry.getValue()));
        return Map.copyOf(snapshot);
    }

    @Override
    public synchronized Map<BlackboardKey<?>, Object> snapshotEntries() {
        return Map.copyOf(objects);
    }
}
