package com.actiongraph.humanreview.api;

import com.actiongraph.action.ActionId;

public final class HumanReviewTaskNotFoundException extends RuntimeException {
    public HumanReviewTaskNotFoundException(String runId, ActionId actionId) {
        super("No human review task found for " + runId + "/" + actionId.value());
    }
}
