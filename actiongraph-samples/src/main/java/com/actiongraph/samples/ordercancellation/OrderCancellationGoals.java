package com.actiongraph.samples.ordercancellation;

import com.actiongraph.planning.Goal;

import java.util.Set;

public final class OrderCancellationGoals {
    private OrderCancellationGoals() {
    }

    public static Goal requestOrderCancellation() {
        return new Goal("requestOrderCancellation", Set.of(OrderCancellationConditions.OPERATIONS_APPROVAL_REQUESTED));
    }
}
