package com.actiongraph.action;

import com.actiongraph.api.Experimental;
import com.actiongraph.planning.Condition;
import org.jspecify.annotations.Nullable;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * Result returned by an Action execution.
 *
 * <p>A successful result allows the runtime to add declared effects and
 * produced conditions to the Blackboard. A failed result triggers reverse
 * compensation for previously successful actions. The message is stored in
 * trace and should not contain unmasked sensitive data.
 *
 * @param success whether the action completed successfully
 * @param message human-readable detail for trace; {@code null} becomes empty
 * @param producedConditions additional symbolic facts produced at runtime
 * @param eventType external event type this action is waiting for; null for normal results
 * @param eventCorrelationId event correlation id; null for normal results
 * @param eventTimeout maximum wait duration requested by the action; null means use runtime default
 */
public record ActionResult(
        boolean success,
        String message,
        List<Condition> producedConditions,
        @Experimental(
                since = "0.2.0",
                value = "External event waits are experimental until MS2 event ingress pilots complete."
        )
        @Nullable String eventType,
        @Experimental(
                since = "0.2.0",
                value = "External event waits are experimental until MS2 event ingress pilots complete."
        )
        @Nullable String eventCorrelationId,
        @Experimental(
                since = "0.2.0",
                value = "External event waits are experimental until MS2 event ingress pilots complete."
        )
        @Nullable Duration eventTimeout
) {
    public ActionResult {
        message = message == null ? "" : message;
        producedConditions = producedConditions == null ? List.of() : List.copyOf(producedConditions);
        if (eventType != null || eventCorrelationId != null || eventTimeout != null) {
            if (!success) {
                throw new IllegalArgumentException("waiting action results must be successful");
            }
            if (eventType == null || eventType.isBlank()) {
                throw new IllegalArgumentException("eventType must not be blank");
            }
            if (eventCorrelationId == null || eventCorrelationId.isBlank()) {
                throw new IllegalArgumentException("eventCorrelationId must not be blank");
            }
            if (eventTimeout != null && (eventTimeout.isZero() || eventTimeout.isNegative())) {
                throw new IllegalArgumentException("eventTimeout must be positive");
            }
            eventType = eventType.trim();
            eventCorrelationId = eventCorrelationId.trim();
        }
    }

    public ActionResult(boolean success, String message, List<Condition> producedConditions) {
        this(success, message, producedConditions, null, null, null);
    }

    public static ActionResult ok(Condition... conditions) {
        return new ActionResult(true, "ok", Arrays.asList(conditions));
    }

    /**
     * Creates a failed result. Returning this is preferred over throwing for
     * expected business failures because the message is traceable and
     * compensation semantics remain explicit.
     *
     * @param message failure detail
     * @return failed action result
     */
    public static ActionResult fail(String message) {
        return new ActionResult(false, message, List.of());
    }

    /**
     * Creates a successful result that pauses the run until an external event
     * with the same type and correlation id is delivered.
     *
     * <p>The action is treated as completed for compensation purposes because
     * the submission side effect may already be visible in an external system.
     * Its compensation method must therefore tolerate both "submitted" and
     * "not submitted" states.
     *
     * @param eventType event category understood by an EventApplier
     * @param correlationId id used to match an incoming event to this run
     * @param timeout optional wait timeout; null uses the executor default
     * @param message trace detail
     * @return waiting action result
     */
    @Experimental(
            since = "0.2.0",
            value = "External event waits are experimental until MS2 event ingress pilots complete."
    )
    public static ActionResult waiting(
            String eventType,
            String correlationId,
            @Nullable Duration timeout,
            String message
    ) {
        return new ActionResult(true, message, List.of(),
                Objects.requireNonNull(eventType, "eventType"),
                Objects.requireNonNull(correlationId, "correlationId"),
                timeout);
    }

    @Experimental(
            since = "0.2.0",
            value = "External event waits are experimental until MS2 event ingress pilots complete."
    )
    public boolean waiting() {
        return eventType != null;
    }
}
