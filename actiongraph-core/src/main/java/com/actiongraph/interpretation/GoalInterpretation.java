package com.actiongraph.interpretation;

import com.actiongraph.planning.Goal;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

public record GoalInterpretation(
        GoalType goalType,
        GoalParameters parameters,
        Set<MissingField> missingFields,
        Optional<ClarificationQuestion> clarificationQuestion,
        Optional<Goal> goal
) {
    public GoalInterpretation {
        Objects.requireNonNull(goalType, "goalType");
        Objects.requireNonNull(parameters, "parameters");
        missingFields = Set.copyOf(Objects.requireNonNull(missingFields, "missingFields"));
        clarificationQuestion = Objects.requireNonNull(clarificationQuestion, "clarificationQuestion");
        goal = Objects.requireNonNull(goal, "goal");
    }

    public static GoalInterpretation ready(GoalType goalType, GoalParameters parameters, Goal goal) {
        return new GoalInterpretation(
                goalType,
                parameters,
                Set.of(),
                Optional.empty(),
                Optional.of(goal)
        );
    }

    public static GoalInterpretation needsClarification(
            GoalType goalType,
            GoalParameters parameters,
            Set<MissingField> missingFields,
            ClarificationQuestion question
    ) {
        return new GoalInterpretation(
                goalType,
                parameters,
                missingFields,
                Optional.of(question),
                Optional.empty()
        );
    }

    public boolean isReady() {
        return goal.isPresent() && missingFields.isEmpty() && clarificationQuestion.isEmpty();
    }
}
