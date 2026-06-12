package com.actiongraph.fingerprint;

import com.actiongraph.action.Action;
import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphConfigurationException;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.planning.Condition;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Collection;
import java.util.Comparator;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Stable fingerprints for audit and bundle validation.
 */
@Experimental(
        since = "0.2.0",
        value = "Fingerprint canonicalization is experimental until DX4 bundle promotion workflows settle."
)
public final class ActionGraphFingerprints {
    private ActionGraphFingerprints() {
    }

    public static String actionGraph(Collection<Action> actions) {
        Objects.requireNonNull(actions, "actions");
        String canonical = actions.stream()
                .sorted(Comparator.comparing(action -> action.id().value()))
                .map(action -> String.join("|",
                        action.id().value(),
                        conditions(action.preconditions()),
                        conditions(action.effects()),
                        action.riskLevel().name(),
                        Boolean.toString(action.requiresHumanReview())))
                .collect(Collectors.joining("\n"));
        return sha256(canonical);
    }

    public static String goal(GoalDefinition definition) {
        Objects.requireNonNull(definition, "definition");
        String canonical = String.join("\n",
                "type=" + definition.type().value(),
                "description=" + definition.description(),
                "targets=" + conditions(definition.goal().targetConditions()),
                "seeds=" + conditions(definition.seedConditions()),
                "parameters=" + parameters(definition),
                "schema=" + (definition.schema().equals(Void.class) ? "" : definition.schema().getName()),
                "parameterSeeding=" + definition.parameterSeeding());
        return sha256(canonical);
    }

    public static String goals(Collection<GoalDefinition> definitions) {
        Objects.requireNonNull(definitions, "definitions");
        String canonical = definitions.stream()
                .sorted(Comparator.comparing(definition -> definition.type().value()))
                .map(ActionGraphFingerprints::goal)
                .collect(Collectors.joining("\n"));
        return sha256(canonical);
    }

    private static String parameters(GoalDefinition definition) {
        return definition.parameters().stream()
                .sorted(Comparator.comparing(GoalParameterDefinition::name))
                .map(parameter -> String.join("|",
                        parameter.name(),
                        parameter.type().getName(),
                        Boolean.toString(parameter.required()),
                        parameter.description(),
                        parameter.example().orElse("")))
                .collect(Collectors.joining(";"));
    }

    private static String conditions(Collection<Condition> conditions) {
        return conditions.stream()
                .map(Condition::key)
                .sorted()
                .collect(Collectors.joining(","));
    }

    private static String sha256(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder output = new StringBuilder(bytes.length * 2);
            for (byte b : bytes) {
                output.append(Character.forDigit((b >> 4) & 0xF, 16));
                output.append(Character.forDigit(b & 0xF, 16));
            }
            return output.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new ActionGraphConfigurationException("SHA-256 is not available", ex);
        }
    }
}
