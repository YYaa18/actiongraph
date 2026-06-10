package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.CancellationEligibility;
import com.actiongraph.samples.ordercancellation.domain.CancellationRequestDraft;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;

public interface CancellationRequestService {
    CancellationRequestDraft createDraft(OrderRecord order, CancellationEligibility eligibility);

    void voidDraft(String requestId);
}
