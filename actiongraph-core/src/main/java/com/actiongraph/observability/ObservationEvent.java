package com.actiongraph.observability;

import com.actiongraph.action.ActionId;

import java.util.Map;
import java.util.Objects;

import org.jspecify.annotations.Nullable;

/**
 * Immutable runtime observation emitted to {@link ObservationSink}.
 *
 * <p>Tags should be low-cardinality operational dimensions such as status,
 * decision, risk level, or success flag. Do not put raw PII, prompts, model
 * output, request bodies, or unbounded business ids into tags. The run id and
 * action id are carried as event fields so adapters can choose whether to use
 * them as tags, exemplars, logs, or ignore them for metric cardinality.
 *
 * @param runId stable run id; never blank
 * @param type event type
 * @param actionId action id when action-scoped, otherwise empty
 * @param tags low-cardinality tag map
 * @param durationNanos elapsed time for completed operations, or {@code 0}
 */
public record ObservationEvent(
        String runId,
        ObservationEventType type,
        String actionId,
        Map<String, String> tags,
        long durationNanos
) {
    public ObservationEvent {
        if (runId == null || runId.isBlank()) {
            throw new IllegalArgumentException("runId must not be blank");
        }
        type = Objects.requireNonNull(type, "type");
        actionId = actionId == null ? "" : actionId;
        tags = Map.copyOf(Objects.requireNonNull(tags, "tags"));
        if (durationNanos < 0) {
            throw new IllegalArgumentException("durationNanos must not be negative");
        }
    }

    public static ObservationEvent of(
            String runId,
            ObservationEventType type,
            @Nullable ActionId actionId,
            Map<String, String> tags
    ) {
        return new ObservationEvent(runId, type, actionIdValue(actionId), tags, 0);
    }

    public static ObservationEvent timed(
            String runId,
            ObservationEventType type,
            @Nullable ActionId actionId,
            Map<String, String> tags,
            long durationNanos
    ) {
        return new ObservationEvent(runId, type, actionIdValue(actionId), tags, durationNanos);
    }

    private static String actionIdValue(@Nullable ActionId actionId) {
        return actionId == null ? "" : actionId.value();
    }
}
