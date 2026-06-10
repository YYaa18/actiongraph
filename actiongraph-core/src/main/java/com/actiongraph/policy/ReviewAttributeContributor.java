package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

import java.util.Map;

public interface ReviewAttributeContributor {
    Map<String, String> contribute(Action action, Blackboard blackboard);
}
