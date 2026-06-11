package com.actiongraph.interpretation;

public record ClarificationQuestion(String text) {
    public ClarificationQuestion {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("ClarificationQuestion text must not be blank");
        }
    }
}
