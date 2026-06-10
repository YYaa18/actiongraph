package com.actiongraph.interpretation;

public interface GoalInterpreter {
    GoalInterpretation interpret(String input);

    default GoalInterpretation interpret(String input, GoalParameters knownParameters) {
        return interpret(input);
    }
}
