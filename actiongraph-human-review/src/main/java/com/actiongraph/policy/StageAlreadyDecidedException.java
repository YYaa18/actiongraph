package com.actiongraph.policy;

import com.actiongraph.action.ActionId;

public final class StageAlreadyDecidedException extends IllegalStateException {
    public StageAlreadyDecidedException(String runId, ActionId actionId, int expectedStageIndex) {
        super("Approval stage was already decided for " + runId + "/" + actionId.value()
                + " at expected stage index " + expectedStageIndex);
    }
}
