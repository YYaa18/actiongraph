package com.actiongraph;

import com.actiongraph.api.Experimental;
import com.actiongraph.exception.ActionGraphInputException;
import com.actiongraph.interpretation.ClarificationQuestion;
import com.actiongraph.interpretation.GoalInterpretation;
import com.actiongraph.runtime.RunResult;

import org.jspecify.annotations.Nullable;

import java.util.Objects;

@Experimental(
        since = "0.2.0",
        value = "ChatResult is experimental until DX2 pilots validate conversational entry-point conventions."
)
public record ChatResult(
        GoalInterpretation interpretation,
        @Nullable RunResult run
) {
    public ChatResult {
        Objects.requireNonNull(interpretation, "interpretation");
    }

    public boolean started() {
        return run != null;
    }

    public ClarificationQuestion clarification() {
        return interpretation.clarificationQuestion()
                .orElseThrow(() -> new ActionGraphInputException("No clarification question is available"));
    }
}
