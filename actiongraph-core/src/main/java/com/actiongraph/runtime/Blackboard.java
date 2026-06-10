package com.actiongraph.runtime;

import com.actiongraph.planning.Condition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public interface Blackboard {
    <T> Optional<T> get(Class<T> type);

    <T> Optional<T> get(BlackboardKey<T> key);

    <T> void put(T value);

    <T> void put(BlackboardKey<T> key, T value);

    boolean has(Class<?> type);

    boolean has(BlackboardKey<?> key);

    <T> List<T> getAll(Class<T> type);

    Set<Condition> conditions();

    void addCondition(Condition condition);

    Map<Class<?>, Object> snapshotObjects();

    Map<BlackboardKey<?>, Object> snapshotEntries();
}
