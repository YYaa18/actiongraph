package com.actiongraph.events;

import com.actiongraph.api.Experimental;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Raw external event content delivered to an {@link EventApplier}.
 *
 * <p>The core runtime treats payloads as opaque data. Applications decide how
 * to parse, validate, and fold them into the Blackboard.
 */
@Experimental(
        since = "0.2.0",
        value = "External event delivery is experimental until MS2 pilots complete."
)
public record EventPayload(String contentType, String body, Map<String, String> attributes) {
    public EventPayload {
        contentType = contentType == null || contentType.isBlank()
                ? "application/octet-stream"
                : contentType;
        body = body == null ? "" : body;
        Map<String, String> normalized = new LinkedHashMap<>();
        if (attributes != null) {
            attributes.forEach((key, value) -> {
                if (key == null || key.isBlank()) {
                    throw new IllegalArgumentException("event payload attribute key must not be blank");
                }
                normalized.put(key, value == null ? "" : value);
            });
        }
        attributes = Map.copyOf(normalized);
    }

    public static EventPayload empty() {
        return new EventPayload("application/octet-stream", "", Map.of());
    }
}
