package com.actiongraph.runtime.api.batch;

import java.util.List;

/**
 * Batch interpretation SPI for applications or model-provider modules that can
 * group, cache, or bypass LLM calls before deterministic ActionGraph execution.
 */
public interface BatchGoalInterpreter {
    List<BatchGoalInterpretation> interpret(List<BatchGoalInput> inputs);
}
