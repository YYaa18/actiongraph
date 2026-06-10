package com.actiongraph.samples.ordercancellation;

import com.actiongraph.planning.Condition;

public final class OrderCancellationConditions {
    public static final String NAMESPACE = "order-cancellation";

    public static final Condition ORDER_ID_PRESENT = Condition.of(NAMESPACE, "ORDER_ID_PRESENT");
    public static final Condition ORDER_LOADED = Condition.of(NAMESPACE, "ORDER_LOADED");
    public static final Condition CANCELLATION_ELIGIBILITY_CHECKED =
            Condition.of(NAMESPACE, "CANCELLATION_ELIGIBILITY_CHECKED");
    public static final Condition CANCELLATION_REQUEST_DRAFTED =
            Condition.of(NAMESPACE, "CANCELLATION_REQUEST_DRAFTED");
    public static final Condition OPERATIONS_APPROVAL_REQUESTED =
            Condition.of(NAMESPACE, "OPERATIONS_APPROVAL_REQUESTED");

    private OrderCancellationConditions() {
    }
}
