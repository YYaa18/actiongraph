package com.actiongraph.governance;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Optional;

public enum NoopAmountExtractor implements AmountExtractor {
    INSTANCE;

    @Override
    public Optional<MonetaryAmount> extract(Action action, Blackboard blackboard) {
        return Optional.empty();
    }
}
