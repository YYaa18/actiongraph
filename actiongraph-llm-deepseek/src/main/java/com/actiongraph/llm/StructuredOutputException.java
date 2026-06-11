package com.actiongraph.llm;

import com.actiongraph.exception.ActionGraphIntegrationException;

/**
 * Raised when a model response cannot be converted into the expected
 * ActionGraph structured output contract.
 */
public final class StructuredOutputException extends ActionGraphIntegrationException {
    private static final long serialVersionUID = 1L;

    public StructuredOutputException(String message) {
        super(message);
    }

    public StructuredOutputException(String message, Throwable cause) {
        super(message, cause);
    }
}
