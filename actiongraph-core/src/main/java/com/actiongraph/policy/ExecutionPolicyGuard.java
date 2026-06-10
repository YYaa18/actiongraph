package com.actiongraph.policy;

import com.actiongraph.action.Action;
import com.actiongraph.runtime.Blackboard;

public interface ExecutionPolicyGuard {
    PolicyDecision evaluate(Action action, Blackboard blackboard);
}
