package com.actiongraph.llm;

import com.actiongraph.exception.ActionGraphIntegrationException;

/**
 * Raised when an LLM provider call cannot be completed or parsed as a provider
 * response.
 */
public final class LlmClientException extends ActionGraphIntegrationException {
    private static final long serialVersionUID = 1L;

    public LlmClientException(String message) {
        super(message);
    }

    public LlmClientException(String message, Throwable cause) {
        super(message, cause);
    }
}
