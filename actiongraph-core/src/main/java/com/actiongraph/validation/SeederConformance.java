package com.actiongraph.validation;

import com.actiongraph.interpretation.GoalBlackboardSeeder;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.planning.Condition;
import com.actiongraph.runtime.InMemoryBlackboard;

import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public final class SeederConformance {
    private SeederConformance() {
    }

    public static void assertSeedsDeclaredConditions(
            GoalBlackboardSeeder seeder,
            GoalParameters sampleParameters,
            GoalDefinition goalDefinition
    ) {
        Objects.requireNonNull(seeder, "seeder");
        Objects.requireNonNull(sampleParameters, "sampleParameters");
        Objects.requireNonNull(goalDefinition, "goalDefinition");
        InMemoryBlackboard blackboard = new InMemoryBlackboard();
        seeder.seed(sampleParameters, blackboard);
        Set<Condition> missing = new LinkedHashSet<>(goalDefinition.seedConditions());
        missing.removeAll(blackboard.conditions());
        if (!missing.isEmpty()) {
            throw new AssertionError("Seeder for goal " + goalDefinition.type().value()
                    + " did not produce declared seed condition(s): " + missing.stream()
                    .map(Condition::key)
                    .sorted()
                    .collect(Collectors.joining(", ")));
        }
    }
}
