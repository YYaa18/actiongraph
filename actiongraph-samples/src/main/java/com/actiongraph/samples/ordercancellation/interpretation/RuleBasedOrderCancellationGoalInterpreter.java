package com.actiongraph.samples.ordercancellation.interpretation;

import com.actiongraph.samples.ordercancellation.OrderCancellationGoals;
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

public final class RuleBasedOrderCancellationGoalInterpreter implements GoalInterpreter {
    private static final Pattern ORDER_ID_PATTERN = Pattern.compile("\\bO\\d{3,}\\b", Pattern.CASE_INSENSITIVE);
    private static final GoalType GOAL_TYPE = OrderCancellationGoalTypes.REQUEST_ORDER_CANCELLATION;
    private static final MissingField ORDER_ID = new MissingField("orderId");

    @Override
    public GoalInterpretation interpret(String input) {
        return interpret(input, GoalParameters.empty());
    }

    @Override
    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
        Optional<String> orderId = extractOrderId(input);
        if (orderId.isPresent()) {
            return ready(orderId.get());
        }
        return knownParameters.get("orderId")
                .map(this::ready)
                .orElseGet(() -> askForOrderId(knownParameters));
    }

    private Optional<String> extractOrderId(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = ORDER_ID_PATTERN.matcher(input);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private GoalInterpretation ready(String orderId) {
        GoalParameters parameters = GoalParameters.of(Map.of("orderId", orderId.toUpperCase()));
        return GoalInterpretation.ready(GOAL_TYPE, parameters, OrderCancellationGoals.requestOrderCancellation());
    }

    private GoalInterpretation askForOrderId(GoalParameters parameters) {
        return GoalInterpretation.needsClarification(
                GOAL_TYPE,
                parameters,
                Set.of(ORDER_ID),
                new ClarificationQuestion("Which order id should I use for the cancellation request?")
        );
    }
}
