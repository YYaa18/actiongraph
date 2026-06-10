package com.actiongraph.action;

import java.util.Collection;
import java.util.Optional;

public interface ActionRegistry {
    void register(Action action);

    Collection<Action> all();

    Optional<Action> byId(ActionId id);
}
