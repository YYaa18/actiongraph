package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimApprovalRequest;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;

public interface ClaimApprovalService {
    ClaimApprovalRequest requestApproval(PayoutApplicationDraft draft);

    void withdraw(String requestId);
}
