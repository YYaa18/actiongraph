package com.actiongraph.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionExecutionPolicy;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.CompensationResult;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;

import java.util.Objects;
import java.util.Set;

final class ExecutionPolicyOverrideAction implements Action {
    private final Action delegate;
    private final ActionExecutionPolicy executionPolicy;

    ExecutionPolicyOverrideAction(Action delegate, ActionExecutionPolicy executionPolicy) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.executionPolicy = Objects.requireNonNull(executionPolicy, "executionPolicy");
    }

    @Override
    public ActionId id() {
        return delegate.id();
    }

    @Override
    public Set<Class<?>> inputTypes() {
        return delegate.inputTypes();
    }

    @Override
    public Set<Class<?>> outputTypes() {
        return delegate.outputTypes();
    }

    @Override
    public Set<Condition> preconditions() {
        return delegate.preconditions();
    }

    @Override
    public Set<Condition> effects() {
        return delegate.effects();
    }

    @Override
    public int cost() {
        return delegate.cost();
    }

    @Override
    public ActionRiskLevel riskLevel() {
        return delegate.riskLevel();
    }

    @Override
    public boolean requiresHumanReview() {
        return delegate.requiresHumanReview();
    }

    @Override
    public ActionExecutionPolicy executionPolicy() {
        return executionPolicy;
    }

    @Override
    public boolean runtimeGuard(Blackboard blackboard) {
        return delegate.runtimeGuard(blackboard);
    }

    @Override
    public ActionResult execute(ExecutionContext context) {
        return delegate.execute(context);
    }

    @Override
    public CompensationResult compensate(ExecutionContext context) {
        return delegate.compensate(context);
    }
}
