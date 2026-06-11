package com.actiongraph.durability;

import com.actiongraph.api.Experimental;

import java.util.Objects;

/**
 * Standard ActionGraph idempotency key shape for remote Action calls.
 *
 * <p>The runtime does not force downstream systems to accept this key. It is a
 * shared convention and helper for HTTP clients, SDKs, and enterprise gateway
 * adapters.
 *
 * @param runId stable runtime run id
 * @param actionId stable action id
 * @param attempt one-based attempt number
 */
@Experimental(
        since = "0.2.0",
        value = "Idempotency conventions are experimental until cross-service pilots complete."
)
public record IdempotencyKey(String runId, String actionId, int attempt) {
    public static final String HEADER_NAME = "X-ActionGraph-Idempotency-Key";

    public IdempotencyKey {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        if (actionId == null || actionId.isBlank()) {
            throw new IllegalArgumentException("actionId must not be blank");
        }
        if (attempt <= 0) {
            throw new IllegalArgumentException("attempt must be positive");
        }
        runId = Objects.requireNonNull(runId, "runId");
        actionId = Objects.requireNonNull(actionId, "actionId");
    }

    public String asHeaderValue() {
        return runId + "/" + actionId + "/" + attempt;
    }
}
