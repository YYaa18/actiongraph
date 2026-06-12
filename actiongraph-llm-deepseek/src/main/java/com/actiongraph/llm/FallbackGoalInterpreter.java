package com.actiongraph.llm;

import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;

import java.util.Objects;
import java.util.function.Consumer;

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
        return interpret(input, knownParameters, ignored -> {
        });
    }

    public GoalInterpretation interpret(
            String input,
            GoalParameters knownParameters,
            Consumer<Boolean> fallbackUsed
    ) {
        Objects.requireNonNull(fallbackUsed, "fallbackUsed");
        try {
            GoalInterpretation interpretation = primary.interpret(input, knownParameters);
            fallbackUsed.accept(false);
            return interpretation;
        } catch (LlmClientException ex) {
            fallbackUsed.accept(true);
            return fallback.interpret(input, knownParameters);
        }
    }
}
