package com.actiongraph.llm;

public interface LlmClient {
    LlmResponse complete(LlmRequest request);
}
