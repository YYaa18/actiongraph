package com.actiongraph.console.studio;

import com.actiongraph.api.Experimental;

/**
 * Text model boundary used by Goal Studio.
 */
@Experimental(
        since = "0.2.0",
        value = "Goal Studio language-model boundary is experimental until drafting workflows settle."
)
public interface GoalStudioLanguageModel {
    String complete(String systemPrompt, String userPrompt, int maxTokens);
}
