package com.actiongraph.samples.claimsprecheck.interpretation;

import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.samples.claimsprecheck.ClaimsPrecheckGoals;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class RuleBasedClaimsPrecheckGoalInterpreter implements GoalInterpreter {
    private static final Pattern CLAIM_ID_PATTERN = Pattern.compile("\\bCLM\\d{3,}\\b", Pattern.CASE_INSENSITIVE);
    private static final GoalType GOAL_TYPE = ClaimsPrecheckGoalTypes.PREPARE_CLAIM_PAYOUT_APPLICATION;
    private static final MissingField CLAIM_ID = new MissingField("claimId");

    @Override
    public GoalInterpretation interpret(String input) {
        return interpret(input, GoalParameters.empty());
    }

    @Override
    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
        Optional<String> claimId = extractClaimId(input);
        if (claimId.isPresent()) {
            return ready(claimId.get());
        }
        return knownParameters.get("claimId")
                .map(this::ready)
                .orElseGet(() -> askForClaimId(knownParameters));
    }

    private Optional<String> extractClaimId(String input) {
        if (input == null || input.isBlank()) {
            return Optional.empty();
        }
        Matcher matcher = CLAIM_ID_PATTERN.matcher(input);
        return matcher.find() ? Optional.of(matcher.group()) : Optional.empty();
    }

    private GoalInterpretation ready(String claimId) {
        GoalParameters parameters = GoalParameters.of(Map.of("claimId", claimId.toUpperCase()));
        return GoalInterpretation.ready(GOAL_TYPE, parameters, ClaimsPrecheckGoals.prepareClaimPayoutApplication());
    }

    private GoalInterpretation askForClaimId(GoalParameters parameters) {
        return GoalInterpretation.needsClarification(
                GOAL_TYPE,
                parameters,
                Set.of(CLAIM_ID),
                new ClarificationQuestion("Which claim id should I use for the payout precheck?")
        );
    }
}
