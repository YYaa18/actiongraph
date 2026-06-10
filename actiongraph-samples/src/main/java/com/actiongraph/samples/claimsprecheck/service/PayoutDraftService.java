package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimPrecheckResult;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;

public interface PayoutDraftService {
    PayoutApplicationDraft createDraft(ClaimRecord claim, ClaimPrecheckResult precheck);

    void voidDraft(String draftId);
}
