package com.actiongraph.events;

import com.actiongraph.api.Experimental;

/**
 * Result of delivering an external event into the runtime.
 */
@Experimental(
        since = "0.2.0",
        value = "External event delivery is experimental until MS2 pilots complete."
)
public enum DeliveryResult {
    /** A waiting run was claimed, updated, and resumed. */
    RESUMED,
    /** Another delivery attempt is already processing the same event key. */
    ALREADY_HANDLED,
    /** No run is currently waiting for the event key. */
    NOT_FOUND,
    /** The runtime has no applier for the supplied event type. */
    APPLIER_MISSING
}
