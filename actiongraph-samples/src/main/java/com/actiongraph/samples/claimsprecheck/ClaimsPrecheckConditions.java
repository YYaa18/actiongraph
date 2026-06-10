package com.actiongraph.samples.claimsprecheck;

import com.actiongraph.planning.Condition;

public final class ClaimsPrecheckConditions {
    public static final String NAMESPACE = "claims";

    public static final Condition CLAIM_ID_PRESENT = Condition.of(NAMESPACE, "CLAIM_ID_PRESENT");
    public static final Condition CLAIM_LOADED = Condition.of(NAMESPACE, "CLAIM_LOADED");
    public static final Condition DOCUMENTS_LOADED = Condition.of(NAMESPACE, "DOCUMENTS_LOADED");
    public static final Condition PRECHECK_COMPLETED = Condition.of(NAMESPACE, "PRECHECK_COMPLETED");
    public static final Condition PAYOUT_DRAFT_CREATED = Condition.of(NAMESPACE, "PAYOUT_DRAFT_CREATED");
    public static final Condition CLAIM_APPROVAL_REQUESTED = Condition.of(NAMESPACE, "CLAIM_APPROVAL_REQUESTED");

    private ClaimsPrecheckConditions() {
    }
}
