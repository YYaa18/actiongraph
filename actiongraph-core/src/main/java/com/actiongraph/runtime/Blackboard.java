package com.actiongraph.runtime;

import com.actiongraph.planning.Condition;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Mutable per-run working memory shared by the planner, runtime, actions, and
 * policy hooks.
 *
 * <p>A Blackboard belongs to one run or one suspended-run snapshot. Do not share
 * it across independent runs. Implementations may be mutable and are not
 * required to be thread-safe unless their documentation says otherwise; the
 * default executor uses a Blackboard from a single run flow at a time.
 *
 * <p>Values can be addressed by type for the common single-instance case or by
 * {@link BlackboardKey} when a domain needs multiple instances of the same type.
 * Symbolic {@link Condition Conditions} are the only facts visible to the
 * planner.
 *
 * <p>Null contract: callers should not store {@code null} values or conditions.
 * Implementations should return empty collections or {@link Optional#empty()}
 * rather than {@code null}.
 */
public interface Blackboard {
    /**
     * Reads the default value for a type.
     *
     * @param type value type; never {@code null}
     * @return value when present and assignable to the type
     */
    <T> Optional<T> get(Class<T> type);

    /**
     * Reads a keyed value.
     *
     * @param key typed Blackboard key; never {@code null}
     * @return value when present and assignable to the key type
     */
    <T> Optional<T> get(BlackboardKey<T> key);

    /**
     * Stores a value under its runtime class and default key.
     *
     * @param value value to store; never {@code null}
     */
    <T> void put(T value);

    /**
     * Stores a value under an explicit typed key.
     *
     * @param key typed Blackboard key; never {@code null}
     * @param value value to store; never {@code null}
     */
    <T> void put(BlackboardKey<T> key, T value);

    /**
     * Returns whether a default value for the type is present.
     *
     * @param type value type; never {@code null}
     * @return {@code true} when present
     */
    boolean has(Class<?> type);

    /**
     * Returns whether a keyed value is present.
     *
     * @param key typed Blackboard key; never {@code null}
     * @return {@code true} when present
     */
    boolean has(BlackboardKey<?> key);

    /**
     * Returns all values assignable to the given type.
     *
     * @param type value type; never {@code null}
     * @return non-null list, possibly empty
     */
    <T> List<T> getAll(Class<T> type);

    /**
     * Returns the current symbolic state visible to the planner.
     *
     * @return non-null set of conditions
     */
    Set<Condition> conditions();

    /**
     * Adds a symbolic fact to the Blackboard.
     *
     * @param condition condition to add; never {@code null}
     */
    void addCondition(Condition condition);

    /**
     * Returns a snapshot of default type-keyed objects.
     *
     * @return non-null snapshot map
     */
    Map<Class<?>, Object> snapshotObjects();

    /**
     * Returns a snapshot of all keyed entries.
     *
     * @return non-null snapshot map
     */
    Map<BlackboardKey<?>, Object> snapshotEntries();
}
