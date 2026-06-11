package com.actiongraph.humanreview.api;

import com.actiongraph.action.ActionId;
import com.actiongraph.exception.ActionGraphNotFoundException;

import java.util.Objects;

/**
 * Raised when a human-review task query or decision targets an unknown task.
 */
public final class HumanReviewTaskNotFoundException extends ActionGraphNotFoundException {
    private static final long serialVersionUID = 1L;

    private final String runId;
    private final ActionId actionId;

    public HumanReviewTaskNotFoundException(String runId, ActionId actionId) {
        super("human review task", runId + "/" + actionId.value(),
                "No human review task found for " + runId + "/" + actionId.value());
        this.runId = Objects.requireNonNull(runId, "runId");
        this.actionId = Objects.requireNonNull(actionId, "actionId");
    }

    public String runId() {
        return runId;
    }

    public ActionId actionId() {
        return actionId;
    }
}
