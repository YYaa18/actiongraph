package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.CancellationRequestDraft;
import com.actiongraph.samples.ordercancellation.domain.OperationsApprovalRequest;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryOperationsApprovalService implements OperationsApprovalService {
    private final AtomicInteger sequence = new AtomicInteger();
    private final boolean failRequest;
    private final List<OperationsApprovalRequest> requests = new ArrayList<>();
    private final List<String> withdrawnApprovalIds = new ArrayList<>();

    public InMemoryOperationsApprovalService() {
        this(false);
    }

    public InMemoryOperationsApprovalService(boolean failRequest) {
        this.failRequest = failRequest;
    }

    @Override
    public synchronized OperationsApprovalRequest request(CancellationRequestDraft draft) {
        if (failRequest) {
            throw new IllegalStateException("Operations approval service unavailable");
        }
        OperationsApprovalRequest request = new OperationsApprovalRequest(
                "OPS-APPROVAL-" + sequence.incrementAndGet(),
                draft.requestId()
        );
        requests.add(request);
        return request;
    }

    @Override
    public synchronized void withdraw(String approvalId) {
        withdrawnApprovalIds.add(approvalId);
    }

    public synchronized List<OperationsApprovalRequest> requests() {
        return List.copyOf(requests);
    }

    public synchronized List<String> withdrawnApprovalIds() {
        return List.copyOf(withdrawnApprovalIds);
    }
}
