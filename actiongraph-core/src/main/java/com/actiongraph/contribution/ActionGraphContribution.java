package com.actiongraph.contribution;

import com.actiongraph.action.Action;
import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalDefinition;

import java.util.List;

public interface ActionGraphContribution {
    default List<Action> actions() {
        return List.of();
    }

    default List<GoalDefinition> goals() {
        return List.of();
    }

    default List<GoalBlackboardSeeder> seeders() {
        return List.of();
    }

    default List<Object> annotatedBeans() {
        return List.of();
    }
}
