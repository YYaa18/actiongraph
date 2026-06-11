package com.actiongraph.governance.humanreview.spring;

import com.actiongraph.action.Action;
import com.actiongraph.action.ActionId;
import com.actiongraph.action.ActionResult;
import com.actiongraph.action.ActionRiskLevel;
import com.actiongraph.action.ExecutionContext;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;
import com.actiongraph.runtime.InMemoryBlackboard;

import java.util.Set;

final class TestActions {
    private TestActions() {
    }

    static Blackboard blackboard() {
        return new InMemoryBlackboard();
    }

    static Action action(String id) {
        return new Action() {
            @Override
            public ActionId id() {
                return new ActionId(id);
            }

            @Override
            public Set<Class<?>> inputTypes() {
                return Set.of();
            }

            @Override
            public Set<Class<?>> outputTypes() {
                return Set.of();
            }

            @Override
            public Set<Condition> preconditions() {
                return Set.of();
            }

            @Override
            public Set<Condition> effects() {
                return Set.of();
            }

            @Override
            public int cost() {
                return 1;
            }

            @Override
            public ActionRiskLevel riskLevel() {
                return ActionRiskLevel.LOW;
            }

            @Override
            public boolean requiresHumanReview() {
                return false;
            }

            @Override
            public ActionResult execute(ExecutionContext context) {
                return ActionResult.ok();
            }
        };
    }
}
