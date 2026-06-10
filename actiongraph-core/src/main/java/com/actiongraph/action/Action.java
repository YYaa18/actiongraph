package com.actiongraph.action;

import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;

import java.util.Set;

public interface Action {
    ActionId id();

    Set<Class<?>> inputTypes();

    Set<Class<?>> outputTypes();

    Set<Condition> preconditions();

    Set<Condition> effects();

    int cost();

    ActionRiskLevel riskLevel();

    boolean requiresHumanReview();

    default boolean runtimeGuard(Blackboard blackboard) {
        return true;
    }

    ActionResult execute(ExecutionContext context);

    default CompensationResult compensate(ExecutionContext context) {
        return CompensationResult.noop();
    }
}
