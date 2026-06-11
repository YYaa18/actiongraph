package com.actiongraph.interpretation;

import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.planning.Goal;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

public final class GoalCatalog {
    private final Map<GoalType, GoalDefinition> definitions = new LinkedHashMap<>();

    public void register(GoalDefinition definition) {
        GoalDefinition previous = definitions.putIfAbsent(definition.type(), definition);
        if (previous != null) {
            throw new ActionGraphConfigurationException("Duplicate goal type: " + definition.type().value());
        }
    }

    public Optional<GoalDefinition> byType(GoalType type) {
        return Optional.ofNullable(definitions.get(type));
    }

    public Collection<GoalDefinition> all() {
        return definitions.values().stream().toList();
    }

    public Map<GoalType, Goal> goalsByType() {
        Map<GoalType, Goal> goals = new LinkedHashMap<>();
        definitions.forEach((type, definition) -> goals.put(type, definition.goal()));
        return Map.copyOf(goals);
    }
}
