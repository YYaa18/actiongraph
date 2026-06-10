package com.actiongraph.llm;

import com.actiongraph.interpretation.GoalParameters;

public interface PromptRenderer {
    LlmRequest render(String input, GoalParameters knownParameters);
}
