package com.actiongraph.samples.renewal.interpretation;

import com.actiongraph.interpretation.GoalInterpreter;
import com.actiongraph.llm.DeepSeekChatClient;
import com.actiongraph.llm.FallbackGoalInterpreter;
import com.actiongraph.llm.GoalCatalogPromptRenderer;
import com.actiongraph.llm.GoalInterpretationJsonParser;
import com.actiongraph.llm.LlmClient;
import com.actiongraph.llm.LlmGoalInterpreter;

import java.util.Optional;

public final class RenewalGoalInterpreterFactory {
    private RenewalGoalInterpreterFactory() {
    }

    public static GoalInterpreter createDefault() {
        return createWithOptionalLlm(DeepSeekChatClient.fromEnvironment());
    }

    public static GoalInterpreter createWithOptionalLlm(Optional<? extends LlmClient> llmClient) {
        GoalInterpreter fallback = new RuleBasedRenewalGoalInterpreter();
        return llmClient
                .<GoalInterpreter>map(client -> new FallbackGoalInterpreter(createLlmInterpreter(client), fallback))
                .orElse(fallback);
    }

    public static LlmGoalInterpreter createLlmInterpreter(LlmClient llmClient) {
        return new LlmGoalInterpreter(
                llmClient,
                new GoalCatalogPromptRenderer(RenewalGoalCatalog.create()),
                new GoalInterpretationJsonParser(RenewalGoalCatalog.create())
        );
    }
}
