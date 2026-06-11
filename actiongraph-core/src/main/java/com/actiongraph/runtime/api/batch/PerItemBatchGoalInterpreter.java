package com.actiongraph.runtime.api.batch;

import com.actiongraph.interpretation.GoalInterpreter;

import java.util.List;
import java.util.Objects;

public final class PerItemBatchGoalInterpreter implements BatchGoalInterpreter {
    private final GoalInterpreter delegate;

    public PerItemBatchGoalInterpreter(GoalInterpreter delegate) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
    }

    @Override
    public List<BatchGoalInterpretation> interpret(List<BatchGoalInput> inputs) {
        return List.copyOf(Objects.requireNonNull(inputs, "inputs")).stream()
                .map(input -> new BatchGoalInterpretation(
                        input.itemId(),
                        delegate.interpret(input.input(), input.knownParameters())
                ))
                .toList();
    }
}
