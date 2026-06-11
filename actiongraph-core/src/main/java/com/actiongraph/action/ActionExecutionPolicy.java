package com.actiongraph.action;

import com.actiongraph.api.Experimental;

import java.time.Duration;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

@Experimental(
        since = "0.1.0",
        value = "Retry and timeout execution policy is experimental until idempotency conventions are proven in pilots."
)
public record ActionExecutionPolicy(
        int maxAttempts,
        Duration backoff,
        @Nullable Duration timeout
) {
    public ActionExecutionPolicy {
        if (maxAttempts <= 0) {
            throw new IllegalArgumentException("maxAttempts must be positive");
        }
        backoff = Objects.requireNonNull(backoff, "backoff");
        if (backoff.isNegative()) {
            throw new IllegalArgumentException("backoff must not be negative");
        }
        if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
            throw new IllegalArgumentException("timeout must be positive");
        }
    }

    public static ActionExecutionPolicy none() {
        return new ActionExecutionPolicy(1, Duration.ZERO, null);
    }
}
