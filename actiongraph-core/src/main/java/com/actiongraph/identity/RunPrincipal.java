package com.actiongraph.identity;

import com.actiongraph.api.Experimental;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Principal on whose behalf an ActionGraph run executes.
 */
@Experimental(
        since = "0.2.0",
        value = "Run principal propagation is experimental until STD1 identity pilots settle."
)
public record RunPrincipal(
        String subject,
        String clientId,
        List<String> delegationChain,
        Map<String, String> attributes
) {
    public static final String ANONYMOUS_SUBJECT = "anonymous";

    public RunPrincipal {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("principal subject must not be blank");
        }
        subject = subject.trim();
        clientId = clientId == null ? "" : clientId.trim();
        delegationChain = safeList(delegationChain, "delegationChain");
        attributes = safeMap(attributes);
    }

    public static RunPrincipal anonymous() {
        return new RunPrincipal(ANONYMOUS_SUBJECT, "", List.of(), Map.of());
    }

    public static RunPrincipal of(String subject) {
        return new RunPrincipal(subject, "", List.of(), Map.of());
    }

    public boolean anonymousPrincipal() {
        return ANONYMOUS_SUBJECT.equals(subject)
                && clientId.isBlank()
                && delegationChain.isEmpty()
                && attributes.isEmpty();
    }

    private static List<String> safeList(List<String> values, String name) {
        if (values == null || values.isEmpty()) {
            return List.of();
        }
        return values.stream()
                .map(value -> {
                    if (value == null || value.isBlank()) {
                        throw new IllegalArgumentException(name + " must not contain blanks");
                    }
                    return value.trim();
                })
                .toList();
    }

    private static Map<String, String> safeMap(Map<String, String> values) {
        if (values == null || values.isEmpty()) {
            return Map.of();
        }
        Map<String, String> safe = new LinkedHashMap<>();
        values.forEach((key, value) -> {
            if (key == null || key.isBlank()) {
                throw new IllegalArgumentException("principal attribute key must not be blank");
            }
            safe.put(key.trim(), Objects.toString(value, ""));
        });
        return Map.copyOf(safe);
    }
}
