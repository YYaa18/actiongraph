package com.actiongraph.runtime.api;

import com.actiongraph.interpretation.GoalInterpretation;

import java.util.Map;

import org.jspecify.annotations.Nullable;

/**
 * Framework entry-point contract for applications that own their own controller,
 * message consumer, batch worker, or gateway adapter.
 */
public interface ActionGraphRuntimeOperations {
    RuntimeInterpretationResponse interpret(String input);

    RuntimeInterpretationResponse interpret(String input, @Nullable Map<String, String> knownParameters);

    RuntimeStartResponse start(String input);

    RuntimeStartResponse start(String input, @Nullable Map<String, String> knownParameters);

    RuntimeStartResponse start(
            String input,
            @Nullable Map<String, String> knownParameters,
            @Nullable Map<String, String> runMetadata
    );

    RuntimeStartResponse start(GoalInterpretation interpretation);

    RuntimeStartResponse start(GoalInterpretation interpretation, @Nullable Map<String, String> runMetadata);

    RuntimeRunResponse resume(String runId);

    RuntimeRunResponse resume(String runId, @Nullable Map<String, String> runMetadata);
}
