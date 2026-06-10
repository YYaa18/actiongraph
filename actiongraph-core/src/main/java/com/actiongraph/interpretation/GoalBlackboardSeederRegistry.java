package com.actiongraph.interpretation;

import com.actiongraph.runtime.Blackboard;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public final class GoalBlackboardSeederRegistry {
    private final Map<GoalType, GoalBlackboardSeeder> seeders = new LinkedHashMap<>();

    public void register(GoalBlackboardSeeder seeder) {
        Objects.requireNonNull(seeder, "seeder");
        GoalBlackboardSeeder previous = seeders.putIfAbsent(seeder.goalType(), seeder);
        if (previous != null) {
            throw new IllegalStateException("Duplicate blackboard seeder for goal type: " + seeder.goalType());
        }
    }

    public Optional<GoalBlackboardSeeder> byGoalType(GoalType goalType) {
        return Optional.ofNullable(seeders.get(goalType));
    }

    public void seed(GoalInterpretation interpretation, Blackboard blackboard) {
        if (!interpretation.isReady()) {
            throw new IllegalStateException("Cannot seed blackboard from an incomplete interpretation");
        }
        GoalBlackboardSeeder seeder = byGoalType(interpretation.goalType())
                .orElseThrow(() -> new IllegalStateException(
                        "No blackboard seeder registered for goal type: " + interpretation.goalType()));
        seeder.seed(interpretation.parameters(), blackboard);
    }
}
