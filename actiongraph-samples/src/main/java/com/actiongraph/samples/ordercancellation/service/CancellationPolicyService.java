package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.CancellationEligibility;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;

public interface CancellationPolicyService {
    CancellationEligibility check(OrderRecord order);
}
