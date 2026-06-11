package com.actiongraph.events;

import com.actiongraph.api.Experimental;
import com.actiongraph.runtime.Blackboard;

/**
 * Application-owned adapter that folds an external event into a Blackboard.
 *
 * <p>Implementations should be deterministic and idempotent for the same
 * payload because transports can retry delivery.
 */
@Experimental(
        since = "0.2.0",
        value = "External event delivery is experimental until MS2 pilots complete."
)
public interface EventApplier {
    /**
     * Event type handled by this applier.
     *
     * @return non-blank event type
     */
    String eventType();

    /**
     * Mutates the Blackboard with facts and values derived from the event.
     *
     * @param payload raw event payload
     * @param blackboard run Blackboard snapshot
     */
    void apply(EventPayload payload, Blackboard blackboard);
}
