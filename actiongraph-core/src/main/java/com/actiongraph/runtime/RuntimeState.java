package com.actiongraph.runtime;

import com.actiongraph.planning.Condition;

import java.util.Set;

public record RuntimeState(Set<Condition> conditions) {
    public RuntimeState {
        conditions = Set.copyOf(conditions);
    }
}
