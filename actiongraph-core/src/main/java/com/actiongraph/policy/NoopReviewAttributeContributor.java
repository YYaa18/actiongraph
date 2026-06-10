package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Map;

public enum NoopReviewAttributeContributor implements ReviewAttributeContributor {
    INSTANCE;

    @Override
    public Map<String, String> contribute(Action action, Blackboard blackboard) {
        return Map.of();
    }
}
