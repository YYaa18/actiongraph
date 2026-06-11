package com.actiongraph.action;

import com.actiongraph.exception.ActionGraphConfigurationException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * Default construction-time {@link ActionRegistry} implementation.
 *
 * <p>The registry preserves registration order for stable inspection but
 * returns snapshots to callers. It is intended to be populated during
 * application startup and then shared read-only. Concurrent mutation is not
 * supported.
 */
public final class DefaultActionRegistry implements ActionRegistry {
    private final Map<ActionId, Action> actions = new LinkedHashMap<>();

    @Override
    public void register(Action action) {
        Objects.requireNonNull(action, "action");
        Action previous = actions.putIfAbsent(action.id(), action);
        if (previous != null) {
            throw new ActionGraphConfigurationException("Duplicate action id: " + action.id().value());
        }
    }

    @Override
    public Collection<Action> all() {
        return List.copyOf(actions.values());
    }

    @Override
    public Optional<Action> byId(ActionId id) {
        return Optional.ofNullable(actions.get(id));
    }
}
