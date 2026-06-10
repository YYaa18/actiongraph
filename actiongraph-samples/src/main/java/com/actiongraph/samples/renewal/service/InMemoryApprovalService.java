package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.domain.QuoteDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryApprovalService implements ApprovalService {
    private final AtomicInteger sequence = new AtomicInteger();
    private final boolean failRequest;
    private final List<ApprovalRequest> requests = new ArrayList<>();
    private final List<String> withdrawnApprovalIds = new ArrayList<>();

    public InMemoryApprovalService() {
        this(false);
    }

    public InMemoryApprovalService(boolean failRequest) {
        this.failRequest = failRequest;
    }

    @Override
    public synchronized ApprovalRequest request(QuoteDraft quoteDraft) {
        if (failRequest) {
            throw new IllegalStateException("Approval service unavailable");
        }
        ApprovalRequest request = new ApprovalRequest("APPROVAL-" + sequence.incrementAndGet(), quoteDraft.quoteId());
        requests.add(request);
        return request;
    }

    @Override
    public synchronized void withdraw(String approvalId) {
        withdrawnApprovalIds.add(approvalId);
    }

    public synchronized List<ApprovalRequest> requests() {
        return List.copyOf(requests);
    }

    public synchronized List<String> withdrawnApprovalIds() {
        return List.copyOf(withdrawnApprovalIds);
    }
}
