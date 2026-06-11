package com.actiongraph.llm;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;
import com.actiongraph.planning.Goal;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

public final class GoalInterpretationJsonParser implements StructuredOutputParser<GoalInterpretation> {
    private final ObjectMapper objectMapper;
    private final Map<GoalType, Goal> goalCatalog;

    public GoalInterpretationJsonParser(Map<GoalType, Goal> goalCatalog) {
        this(new ObjectMapper(), goalCatalog);
    }

    public GoalInterpretationJsonParser(GoalCatalog goalCatalog) {
        this(new ObjectMapper(), goalCatalog.goalsByType());
    }

    public GoalInterpretationJsonParser(ObjectMapper objectMapper, Map<GoalType, Goal> goalCatalog) {
        this.objectMapper = objectMapper;
        this.goalCatalog = Map.copyOf(goalCatalog);
    }

    @Override
    public GoalInterpretation parse(String text) {
        JsonNode root = parseRoot(text);
        GoalType goalType = new GoalType(requireString(root, "goalType"));
        GoalParameters parameters = new GoalParameters(parseStringMap(root.path("parameters")));
        Set<MissingField> missingFields = parseMissingFields(root.path("missingFields"));
        Optional<ClarificationQuestion> question = parseQuestion(root.path("clarificationQuestion"));

        if (!missingFields.isEmpty()) {
            return GoalInterpretation.needsClarification(
                    goalType,
                    parameters,
                    missingFields,
                    question.orElseGet(() -> new ClarificationQuestion("Please provide the missing fields."))
            );
        }

        Goal goal = Optional.ofNullable(goalCatalog.get(goalType))
                .orElseThrow(() -> new StructuredOutputException("Unknown goalType: " + goalType.value()));
        return GoalInterpretation.ready(goalType, parameters, goal);
    }

    private String extractJsonObject(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline >= 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }
        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new StructuredOutputException("No JSON object found in LLM output");
        }
        return trimmed.substring(start, end + 1);
    }

    private JsonNode parseRoot(String text) {
        try {
            return objectMapper.readTree(extractJsonObject(text));
        } catch (Exception ex) {
            throw new StructuredOutputException("Could not parse LLM JSON output", ex);
        }
    }

    private String requireString(JsonNode root, String field) {
        JsonNode value = root.path(field);
        if (value.isTextual() && !value.asText().isBlank()) {
            return value.asText();
        }
        throw new StructuredOutputException("Expected string field: " + field);
    }

    private Map<String, String> parseStringMap(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Map.of();
        }
        if (!value.isObject()) {
            throw new StructuredOutputException("Expected parameters object");
        }
        Map<String, String> result = new LinkedHashMap<>();
        value.properties().forEach(entry -> {
            JsonNode parameterValue = entry.getValue();
            if (!parameterValue.isNull()) {
                result.put(entry.getKey(), parameterValue.asText());
            }
        });
        return result;
    }

    private Set<MissingField> parseMissingFields(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Set.of();
        }
        if (!value.isArray()) {
            throw new StructuredOutputException("Expected missingFields array");
        }
        Set<MissingField> fields = new LinkedHashSet<>();
        for (JsonNode item : value) {
            if (item.isTextual() && !item.asText().isBlank()) {
                String field = item.asText();
                fields.add(new MissingField(field));
            } else {
                throw new StructuredOutputException("Missing field names must be strings");
            }
        }
        return fields;
    }

    private Optional<ClarificationQuestion> parseQuestion(JsonNode value) {
        if (value == null || value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (value.isTextual() && !value.asText().isBlank()) {
            return Optional.of(new ClarificationQuestion(value.asText()));
        }
        throw new StructuredOutputException("clarificationQuestion must be string or null");
    }
}
