package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Map;

/**
 * Review attribute contributor that adds no attributes.
 *
 * <p>The singleton is immutable and safe to share across concurrent runs.
 */
public enum NoopReviewAttributeContributor implements ReviewAttributeContributor {
    INSTANCE;

    @Override
    public Map<String, String> contribute(Action action, Blackboard blackboard) {
        return Map.of();
    }
}
