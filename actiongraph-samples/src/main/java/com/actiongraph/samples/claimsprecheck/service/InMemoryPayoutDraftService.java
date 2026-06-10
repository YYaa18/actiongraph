package com.actiongraph.samples.claimsprecheck.service;

import com.actiongraph.samples.claimsprecheck.domain.ClaimPrecheckResult;
import com.actiongraph.samples.claimsprecheck.domain.ClaimRecord;
import com.actiongraph.samples.claimsprecheck.domain.PayoutApplicationDraft;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryPayoutDraftService implements PayoutDraftService {
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<PayoutApplicationDraft> drafts = new ArrayList<>();
    private final List<String> voidedDraftIds = new ArrayList<>();

    @Override
    public synchronized PayoutApplicationDraft createDraft(ClaimRecord claim, ClaimPrecheckResult precheck) {
        PayoutApplicationDraft draft = new PayoutApplicationDraft(
                "CLAIM-DRAFT-" + sequence.incrementAndGet(),
                claim.claimId(),
                claim.claimedAmount(),
                claim.currency()
        );
        drafts.add(draft);
        return draft;
    }

    @Override
    public synchronized void voidDraft(String draftId) {
        voidedDraftIds.add(draftId);
    }

    public synchronized List<PayoutApplicationDraft> drafts() {
        return List.copyOf(drafts);
    }

    public synchronized List<String> voidedDraftIds() {
        return List.copyOf(voidedDraftIds);
    }
}
