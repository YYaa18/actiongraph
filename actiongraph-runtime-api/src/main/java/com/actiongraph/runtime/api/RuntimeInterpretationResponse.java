package com.actiongraph.runtime.api;

import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.planning.Condition;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;

public record RuntimeInterpretationResponse(
        String goalType,
        Map<String, String> parameters,
        List<String> missingFields,
        String clarificationQuestion,
        boolean ready,
        String goalName,
        List<String> targetConditions
) {
    public RuntimeInterpretationResponse {
        goalType = requireText(goalType, "goalType");
        parameters = Map.copyOf(new TreeMap<>(Objects.requireNonNull(parameters, "parameters")));
        missingFields = List.copyOf(Objects.requireNonNull(missingFields, "missingFields"));
        clarificationQuestion = clarificationQuestion == null ? "" : clarificationQuestion;
        goalName = goalName == null ? "" : goalName;
        targetConditions = List.copyOf(Objects.requireNonNull(targetConditions, "targetConditions"));
    }

    public static RuntimeInterpretationResponse from(GoalInterpretation interpretation) {
        Objects.requireNonNull(interpretation, "interpretation");
        return new RuntimeInterpretationResponse(
                interpretation.goalType().value(),
                interpretation.parameters().values(),
                interpretation.missingFields().stream()
                        .map(MissingField::name)
                        .sorted()
                        .toList(),
                interpretation.clarificationQuestion()
                        .map(question -> question.text())
                        .orElse(""),
                interpretation.isReady(),
                interpretation.goal()
                        .map(goal -> goal.name())
                        .orElse(""),
                interpretation.goal().stream()
                        .flatMap(goal -> goal.targetConditions().stream())
                        .sorted(Comparator.comparing(Condition::key))
                        .map(Condition::key)
                        .toList()
        );
    }

    private static String requireText(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
