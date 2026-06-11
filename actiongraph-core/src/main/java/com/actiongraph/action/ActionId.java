package com.actiongraph.action;

/**
 * Stable identifier for an {@link Action}.
 *
 * <p>The id appears in plans, traces, review tasks, compensation stacks, and
 * suspended-run snapshots. Treat it as durable API: renaming an action id can
 * break resume, audit history, and external approval callbacks.
 *
 * @param value non-blank identifier, usually namespaced by domain
 */
public record ActionId(String value) {
    public ActionId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("ActionId value must not be blank");
        }
    }

    @Override
    public String toString() {
        return value;
    }
}
