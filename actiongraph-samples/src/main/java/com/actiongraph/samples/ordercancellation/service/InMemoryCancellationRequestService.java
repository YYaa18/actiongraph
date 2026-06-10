package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.CancellationEligibility;
import com.actiongraph.samples.ordercancellation.domain.CancellationRequestDraft;
import com.actiongraph.samples.ordercancellation.domain.OrderRecord;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryCancellationRequestService implements CancellationRequestService {
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<CancellationRequestDraft> drafts = new ArrayList<>();
    private final List<String> voidedRequestIds = new ArrayList<>();

    @Override
    public synchronized CancellationRequestDraft createDraft(OrderRecord order, CancellationEligibility eligibility) {
        CancellationRequestDraft draft = new CancellationRequestDraft(
                "CANCEL-" + sequence.incrementAndGet(),
                order.orderId()
        );
        drafts.add(draft);
        return draft;
    }

    @Override
    public synchronized void voidDraft(String requestId) {
        voidedRequestIds.add(requestId);
    }

    public synchronized List<CancellationRequestDraft> drafts() {
        return List.copyOf(drafts);
    }

    public synchronized List<String> voidedRequestIds() {
        return List.copyOf(voidedRequestIds);
    }
}
