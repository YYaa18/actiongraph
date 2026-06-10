package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.ApprovalRequest;
import com.actiongraph.samples.renewal.domain.QuoteDraft;

public interface ApprovalService {
    ApprovalRequest request(QuoteDraft quoteDraft);

    void withdraw(String approvalId);
}
