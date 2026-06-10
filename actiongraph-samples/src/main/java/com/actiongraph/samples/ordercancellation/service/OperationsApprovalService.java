package com.actiongraph.samples.ordercancellation.service;

import com.actiongraph.samples.ordercancellation.domain.CancellationRequestDraft;
import com.actiongraph.samples.ordercancellation.domain.OperationsApprovalRequest;

public interface OperationsApprovalService {
    OperationsApprovalRequest request(CancellationRequestDraft draft);

    void withdraw(String approvalId);
}
