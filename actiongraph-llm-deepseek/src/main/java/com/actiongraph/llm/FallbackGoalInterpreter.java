package com.actiongraph.llm;

import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;

import java.util.Objects;

public final class FallbackGoalInterpreter implements GoalInterpreter {
    private final GoalInterpreter primary;
    private final GoalInterpreter fallback;

    public FallbackGoalInterpreter(GoalInterpreter primary, GoalInterpreter fallback) {
        this.primary = Objects.requireNonNull(primary, "primary");
        this.fallback = Objects.requireNonNull(fallback, "fallback");
    }

    @Override
    public GoalInterpretation interpret(String input) {
        return interpret(input, GoalParameters.empty());
    }

    @Override
    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
        try {
            return primary.interpret(input, knownParameters);
        } catch (LlmClientException ex) {
            return fallback.interpret(input, knownParameters);
        }
    }
}
