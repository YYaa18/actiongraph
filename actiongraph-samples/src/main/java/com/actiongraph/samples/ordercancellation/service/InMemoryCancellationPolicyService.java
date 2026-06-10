package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.CancellationEligibility;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;

public final class InMemoryCancellationPolicyService implements CancellationPolicyService {
    @Override
    public CancellationEligibility check(OrderRecord order) {
        return order.shipped()
                ? new CancellationEligibility(false, "Order has already shipped")
                : new CancellationEligibility(true, "Order has not shipped");
    }
}
