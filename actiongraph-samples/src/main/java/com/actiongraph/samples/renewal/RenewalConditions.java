package com.actiongraph.samples.renewal;

import com.actiongraph.planning.Condition;

public final class RenewalConditions {
    public static final String NAMESPACE = "renewal";

    public static final Condition CUSTOMER_ID_PRESENT = Condition.of(NAMESPACE, "CUSTOMER_ID_PRESENT");
    public static final Condition CUSTOMER_PROFILE_LOADED = Condition.of(NAMESPACE, "CUSTOMER_PROFILE_LOADED");
    public static final Condition CURRENT_CONTRACT_LOADED = Condition.of(NAMESPACE, "CURRENT_CONTRACT_LOADED");
    public static final Condition RENEWAL_ELIGIBILITY_CHECKED = Condition.of(NAMESPACE, "RENEWAL_ELIGIBILITY_CHECKED");
    public static final Condition QUOTE_DRAFT_CREATED = Condition.of(NAMESPACE, "QUOTE_DRAFT_CREATED");
    public static final Condition SALES_APPROVAL_REQUESTED = Condition.of(NAMESPACE, "SALES_APPROVAL_REQUESTED");

    private RenewalConditions() {
    }
}
