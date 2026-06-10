package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Optional;

public interface AmountExtractor {
    Optional<MonetaryAmount> extract(Action action, Blackboard blackboard);
}
