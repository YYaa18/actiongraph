package com.actiongraph.action;

import java.util.Collection;
import java.util.Optional;

/**
 * Registry of Actions available to a single planning/execution scope.
 *
 * <p>Implementations decide their own concurrency model. The default registry
 * is intended for construction-time registration followed by read-only use.
 * Callers should not mutate a registry while a run is using it.
 */
public interface ActionRegistry {
    /**
     * Registers an action under its stable id.
     *
     * @param action action to register; never {@code null}
     * @throws RuntimeException when the implementation rejects duplicate ids
     */
    void register(Action action);

    /**
     * Returns all registered actions as a snapshot or read-only collection.
     *
     * @return non-null collection of actions
     */
    Collection<Action> all();

    /**
     * Looks up a registered action by id.
     *
     * @param id action id; never {@code null}
     * @return action when present
     */
    Optional<Action> byId(ActionId id);
}
