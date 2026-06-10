package com.actiongraph.llm;

public record LlmResponse(String text) {
    public LlmResponse {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("LlmResponse text must not be blank");
        }
    }
}
