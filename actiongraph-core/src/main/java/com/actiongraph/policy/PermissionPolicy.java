package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

public interface PermissionPolicy {
    default boolean canExecute(Action action, Blackboard blackboard) {
        return true;
    }
}
