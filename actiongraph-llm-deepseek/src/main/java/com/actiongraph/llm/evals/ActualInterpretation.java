package com.actiongraph.llm.evals;

import com.actiongraph.api.Experimental;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.MissingField;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Normalized actual interpretation fields used in diff reports.
 */
@Experimental(
        since = "0.2.0",
        value = "Golden-set evaluation contracts are experimental until STD3 pilots settle."
)
public record ActualInterpretation(
        String goalType,
        Map<String, String> parameters,
        boolean ready,
        boolean clarification,
        Set<String> missingFields,
        boolean unknownGoal
) {
    public ActualInterpretation {
        parameters = Map.copyOf(parameters);
        missingFields = Set.copyOf(missingFields);
    }

    static ActualInterpretation from(GoalInterpretation interpretation) {
        Set<String> missing = interpretation.missingFields().stream()
                .map(MissingField::name)
                .collect(Collectors.toUnmodifiableSet());
        return new ActualInterpretation(
                interpretation.goalType().value(),
                interpretation.parameters().values(),
                interpretation.isReady(),
                !interpretation.isReady(),
                missing,
                "unknown".equalsIgnoreCase(interpretation.goalType().value())
        );
    }
}
