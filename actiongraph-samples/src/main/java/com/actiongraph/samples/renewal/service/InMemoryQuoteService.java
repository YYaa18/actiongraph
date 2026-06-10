package com.actiongraph.samples.renewal.service;

import com.actiongraph.samples.renewal.domain.CustomerProfile;
import com.actiongraph.samples.renewal.domain.QuoteDraft;
import com.actiongraph.samples.renewal.domain.RenewalEligibility;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public final class InMemoryQuoteService implements QuoteService {
    private final AtomicInteger sequence = new AtomicInteger();
    private final List<QuoteDraft> drafts = new ArrayList<>();
    private final List<String> voidedQuoteIds = new ArrayList<>();

    @Override
    public synchronized QuoteDraft createDraft(CustomerProfile profile, RenewalEligibility eligibility) {
        QuoteDraft draft = new QuoteDraft("QUOTE-" + sequence.incrementAndGet(), profile.customerId());
        drafts.add(draft);
        return draft;
    }

    @Override
    public synchronized void voidDraft(String quoteId) {
        voidedQuoteIds.add(quoteId);
    }

    public synchronized List<QuoteDraft> drafts() {
        return List.copyOf(drafts);
    }

    public synchronized List<String> voidedQuoteIds() {
        return List.copyOf(voidedQuoteIds);
    }
}
