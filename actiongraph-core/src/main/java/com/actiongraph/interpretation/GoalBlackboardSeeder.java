package com.actiongraph.interpretation;

import com.actiongraph.api.Experimental;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.Blackboard;

import java.util.Optional;
import java.util.Set;

public interface GoalBlackboardSeeder {
    GoalType goalType();

    void seed(GoalParameters parameters, Blackboard blackboard);

    @Experimental(
            since = "0.2.0",
            value = "Declared seeder conditions are experimental until annotated seeder validation settles."
    )
    default Optional<Set<Condition>> declaredSeedConditions() {
        return Optional.empty();
    }
}
