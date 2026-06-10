package com.actiongraph.llm;

import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.interpretation.GoalParameters;
import com.actiongraph.interpretation.GoalType;
import com.actiongraph.interpretation.MissingField;

import java.util.Objects;
import java.util.Set;

public final class LlmGoalInterpreter implements GoalInterpreter {
    private static final GoalType UNKNOWN = new GoalType("unknown");

    private final LlmClient llmClient;
    private final PromptRenderer promptRenderer;
    private final StructuredOutputParser<GoalInterpretation> parser;

    public LlmGoalInterpreter(
            LlmClient llmClient,
            PromptRenderer promptRenderer,
            StructuredOutputParser<GoalInterpretation> parser
    ) {
        this.llmClient = Objects.requireNonNull(llmClient, "llmClient");
        this.promptRenderer = Objects.requireNonNull(promptRenderer, "promptRenderer");
        this.parser = Objects.requireNonNull(parser, "parser");
    }

    @Override
    public GoalInterpretation interpret(String input) {
        return interpret(input, GoalParameters.empty());
    }

    @Override
    public GoalInterpretation interpret(String input, GoalParameters knownParameters) {
        LlmRequest request = promptRenderer.render(input, knownParameters);
        LlmResponse response = llmClient.complete(request);
        try {
            return parser.parse(response.text());
        } catch (StructuredOutputException ex) {
            return GoalInterpretation.needsClarification(
                    UNKNOWN,
                    knownParameters,
                    Set.of(new MissingField("validGoalInterpretation")),
                    new ClarificationQuestion("I could not interpret that request reliably. Please include the goal and customer id.")
            );
        }
    }
}
