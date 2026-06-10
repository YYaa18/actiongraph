package com.actiongraph.samples.renewal.interpretation;

import com.actiongraph.samples.renewal.RenewalGoals;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleBasedRenewalGoalInterpreter implements GoalInterpreter {
    private static final Pattern CUSTOMER_ID_PATTERN = Pattern.compile("\\bC\\d{3,}\\b", Pattern.CASE_INSENSITIVE);
    private static final GoalType GOAL_TYPE = RenewalGoalTypes.PREPARE_RENEWAL_QUOTE;
    private static final MissingField CUSTOMER_ID = new MissingField("customerId");

    @Override
    public GoalInterpretation interpret(String input) {
        return interpret(input, GoalParameters.empty());
    }

    @Override
    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
        if (input == null || input.isBlank()) {
            return knownParameters.get("customerId")
                    .map(this::ready)
                    .orElseGet(() -> askForCustomerId(knownParameters));
        }

        Optional<String> customerId = extractCustomerId(input);
        if (customerId.isEmpty()) {
            return knownParameters.get("customerId")
                    .map(this::ready)
                    .orElseGet(() -> askForCustomerId(knownParameters));
        }

        return ready(customerId.get());
    }

    private Optional<String> extractCustomerId(String input) {
        Matcher matcher = CUSTOMER_ID_PATTERN.matcher(input);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private GoalInterpretation askForCustomerId(GoalParameters parameters) {
        return GoalInterpretation.needsClarification(
                GOAL_TYPE,
                parameters,
                Set.of(CUSTOMER_ID),
                new ClarificationQuestion("Which customer id should I use for the renewal quote?")
        );
    }

    private GoalInterpretation ready(String customerId) {
        GoalParameters parameters = GoalParameters.of(Map.of("customerId", customerId.toUpperCase()));
        return GoalInterpretation.ready(GOAL_TYPE, parameters, RenewalGoals.prepareRenewalQuote());
    }
}
