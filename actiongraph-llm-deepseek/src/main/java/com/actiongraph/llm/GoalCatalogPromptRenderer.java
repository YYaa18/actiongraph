package com.actiongraph.llm;

import com.actiongraph.interpretation.GoalCatalog;
import com.actiongraph.interpretation.GoalDefinition;
import com.actiongraph.interpretation.GoalParameterDefinition;
import com.actiongraph.interpretation.GoalParameters;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.StringReader;
import java.io.StringWriter;
import java.util.List;
import java.util.Map;

public final class GoalCatalogPromptRenderer implements PromptRenderer {
    private static final int DEFAULT_MAX_TOKENS = 700;
    private static final String USER_TEMPLATE = """
            Known parameters: {{knownParameters}}
            User request: {{input}}
            """;

    private final GoalCatalog catalog;
    private final int maxTokens;
    private final Mustache userPromptTemplate;

    public GoalCatalogPromptRenderer(GoalCatalog catalog) {
        this(catalog, DEFAULT_MAX_TOKENS);
    }

    public GoalCatalogPromptRenderer(GoalCatalog catalog, int maxTokens) {
        if (catalog.all().isEmpty()) {
            throw new IllegalArgumentException("GoalCatalog must contain at least one goal");
        }
        if (maxTokens <= 0) {
            throw new IllegalArgumentException("maxTokens must be > 0");
        }
        this.catalog = catalog;
        this.maxTokens = maxTokens;
        MustacheFactory factory = new DefaultMustacheFactory();
        this.userPromptTemplate = factory.compile(new StringReader(USER_TEMPLATE), "goal-catalog-user-prompt");
    }

    @Override
    public LlmRequest render(String input, GoalParameters knownParameters) {
        return new LlmRequest(renderSystemPrompt(), renderUserPrompt(input, knownParameters), maxTokens);
    }

    private String renderSystemPrompt() {
        StringBuilder builder = new StringBuilder();
        builder.append("""
                You are a goal interpreter for a deterministic GOAP runtime.
                Convert the user's request into exactly one supported goal.
                You must return valid JSON only. Do not return markdown, prose, plans, actions, or execution steps.

                Supported goals:
                """);
        for (GoalDefinition goal : catalog.all()) {
            builder.append("- ")
                    .append(goal.type().value())
                    .append(": ")
                    .append(goal.description())
                    .append('\n');
            if (!goal.parameters().isEmpty()) {
                builder.append("  Parameters:\n");
                for (GoalParameterDefinition parameter : goal.parameters()) {
                    builder.append("  - ")
                            .append(parameter.name())
                            .append(parameter.required() ? " (required)" : " (optional)")
                            .append(": ")
                            .append(parameter.description());
                    parameter.example().ifPresent(example -> builder.append(" Example: ").append(example));
                    builder.append('\n');
                }
            }
        }
        builder.append("""

                Return this JSON shape:
                {
                  "goalType": "<one supported goalType>",
                  "parameters": {"<parameterName>": "<value>"},
                  "missingFields": [],
                  "clarificationQuestion": null
                }

                If required fields are missing, return the selected goalType, any known parameters, every missing
                required field name in missingFields, and one concise clarificationQuestion.
                """);
        return builder.toString();
    }

    private String renderUserPrompt(String input, GoalParameters knownParameters) {
        StringWriter writer = new StringWriter();
        userPromptTemplate.execute(writer, Map.of(
                "knownParameters", knownParameters.values(),
                "input", input == null ? "" : input
        ));
        return writer.toString();
    }
}
