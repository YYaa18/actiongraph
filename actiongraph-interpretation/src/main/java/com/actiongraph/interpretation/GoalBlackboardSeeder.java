package com.actiongraph.interpretation;

import com.actiongraph.runtime.Blackboard;

public interface GoalBlackboardSeeder {
    GoalType goalType();

    void seed(GoalParameters parameters, Blackboard blackboard);
}
