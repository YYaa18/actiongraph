package com.actiongraph.runtime;

import com.actiongraph.action.ExecutionContext;
import com.actiongraph.trace.TraceRepository;

public record DefaultExecutionContext(
        Blackboard blackboard,
        TraceRepository trace,
        String runId
) implements ExecutionContext {
}
