package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimApprovalRequest;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryClaimApprovalService implements ClaimApprovalService {
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<ClaimApprovalRequest> requests = new ArrayList<>();
    private final List<String> withdrawnRequestIds = new ArrayList<>();
    private final boolean failOnRequest;

    public InMemoryClaimApprovalService() {
        this(false);
    }

    public InMemoryClaimApprovalService(boolean failOnRequest) {
        this.failOnRequest = failOnRequest;
    }

    @Override
    public synchronized ClaimApprovalRequest requestApproval(PayoutApplicationDraft draft) {
        if (failOnRequest) {
            throw new IllegalStateException("claim approval service unavailable");
        }
        ClaimApprovalRequest request = new ClaimApprovalRequest(
                "CLAIM-APPROVAL-" + sequence.incrementAndGet(),
                draft.draftId()
        );
        requests.add(request);
        return request;
    }

    @Override
    public synchronized void withdraw(String requestId) {
        withdrawnRequestIds.add(requestId);
    }

    public synchronized List<ClaimApprovalRequest> requests() {
        return List.copyOf(requests);
    }

    public synchronized List<String> withdrawnRequestIds() {
        return List.copyOf(withdrawnRequestIds);
    }
}
