package com.actiongraph.action;

import com.actiongraph.runtime.Blackboard;
import com.actiongraph.trace.TraceRepository;

public interface ExecutionContext {
    Blackboard blackboard();

    TraceRepository trace();

    String runId();
}
