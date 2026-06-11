package com.actiongraph.policy;

import com.actiongraph.action.ActionId;
import com.actiongraph.exception.ActionGraphConflictException;

import java.util.Objects;

/**
 * Raised when a human-review callback tries to decide a stage that has already
 * been decided.
 */
public final class StageAlreadyDecidedException extends ActionGraphConflictException {
    private static final long serialVersionUID = 1L;

    private final String runId;
    private final ActionId actionId;
    private final int expectedStageIndex;

    public StageAlreadyDecidedException(String runId, ActionId actionId, int expectedStageIndex) {
        super("Approval stage was already decided for " + runId + "/" + actionId.value()
                + " at expected stage index " + expectedStageIndex);
        this.runId = Objects.requireNonNull(runId, "runId");
        this.actionId = Objects.requireNonNull(actionId, "actionId");
        this.expectedStageIndex = expectedStageIndex;
    }

    public String runId() {
        return runId;
    }

    public ActionId actionId() {
        return actionId;
    }

    public int expectedStageIndex() {
        return expectedStageIndex;
    }
}
