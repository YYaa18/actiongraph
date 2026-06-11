package com.actiongraph.interpretation;

import org.jspecify.annotations.Nullable;

public interface GoalInterpreter {
    GoalInterpretation interpret(String input);

    default GoalInterpretation interpret(String input, @Nullable GoalParameters knownParameters) {
        return interpret(input);
    }
}
